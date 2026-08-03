package com.sapcommercetools.flexsearch;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * A minimal, dependency-free client for executing FlexibleSearch statements
 * against an SAP Commerce HAC (hybris administration console) instance.
 *
 * <p>It uses only the JDK ({@link java.net.http.HttpClient}) and drives the same
 * HTTP surface a browser would:
 * <ol>
 *   <li>{@link #login()} performs the Spring Security form login: it GETs
 *       {@code /login} to obtain the {@code _csrf} form token, POSTs credentials
 *       to {@code /j_spring_security_check}, then reads the AJAX CSRF token from
 *       the authed home page's {@code <meta name="_csrf">} tag.</li>
 *   <li>{@link #execute(String, int)} / {@link #tryExecute(String, int)} POST
 *       the query to {@code /console/flexsearch/execute} with the
 *       {@code X-CSRF-TOKEN} header, reusing the session cookie jar.</li>
 * </ol>
 *
 * <p>When {@link HacConfig#insecureTls()} is {@code true} the client trusts
 * self-signed certificates and disables hostname verification — appropriate for
 * a local sample instance at {@code https://localhost:9002}, never for
 * production.
 *
 * <p>This client is <em>not</em> thread-safe: call {@link #login()} once, then
 * issue queries serially.
 */
public final class HacFlexibleSearchClient {

    private static final Pattern CSRF_INPUT = Pattern.compile(
            "name=\"_csrf\"[^>]*value=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern CSRF_INPUT_VALUE_FIRST = Pattern.compile(
            "value=\"([^\"]+)\"[^>]*name=\"_csrf\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern CSRF_META = Pattern.compile(
            "<meta[^>]*name=\"_csrf\"[^>]*content=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern CSRF_META_CONTENT_FIRST = Pattern.compile(
            "<meta[^>]*content=\"([^\"]+)\"[^>]*name=\"_csrf\"", Pattern.CASE_INSENSITIVE);

    private final HacConfig config;
    private final HttpClient http;

    /** AJAX CSRF token read from an authed page's meta tag; set by {@link #login()}. */
    private String csrfToken;

    /**
     * Creates a client for the given configuration. No network call is made
     * until {@link #login()} is invoked.
     *
     * @param config the connection configuration (non-null)
     */
    public HacFlexibleSearchClient(HacConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        this.config = config;
        HttpClient.Builder builder = HttpClient.newBuilder()
                .cookieHandler(new CookieManager())
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL);
        if (config.insecureTls()) {
            builder.sslContext(trustAllContext());
            // Disable hostname verification (NoopHostnameVerifier equivalent).
            SSLParameters params = new SSLParameters();
            params.setEndpointIdentificationAlgorithm(null);
            builder.sslParameters(params);
        }
        this.http = builder.build();
    }

    /**
     * Convenience factory reading configuration from environment variables.
     *
     * @return a client bound to {@link HacConfig#fromEnv()}
     */
    public static HacFlexibleSearchClient fromEnv() {
        return new HacFlexibleSearchClient(HacConfig.fromEnv());
    }

    /**
     * Performs the HAC Spring Security form login and captures the AJAX CSRF
     * token used for subsequent POSTs.
     *
     * @throws IOException if a request fails or the expected tokens are missing
     * @throws InterruptedException if the calling thread is interrupted
     */
    public void login() throws IOException, InterruptedException {
        // 1. GET /login → extract the form _csrf token.
        HttpResponse<String> loginPage = http.send(
                get(config.baseUrl() + "/login"),
                HttpResponse.BodyHandlers.ofString());
        String formCsrf = firstMatch(loginPage.body(), CSRF_INPUT, CSRF_INPUT_VALUE_FIRST);
        if (formCsrf == null) {
            throw new IOException("could not find _csrf token on /login (status "
                    + loginPage.statusCode() + ")");
        }

        // 2. POST credentials to /j_spring_security_check.
        Map<String, String> form = new LinkedHashMap<>();
        form.put("j_username", config.user());
        form.put("j_password", config.password());
        form.put("_csrf", formCsrf);
        HttpResponse<String> auth = http.send(
                postForm(config.baseUrl() + "/j_spring_security_check", form, null),
                HttpResponse.BodyHandlers.ofString());
        if (auth.statusCode() >= 400) {
            throw new IOException("login POST failed with status " + auth.statusCode());
        }

        // 3. GET / → read AJAX CSRF token from the <meta name="_csrf"> tag.
        HttpResponse<String> home = http.send(
                get(config.baseUrl() + "/"),
                HttpResponse.BodyHandlers.ofString());
        String metaCsrf = firstMatch(home.body(), CSRF_META, CSRF_META_CONTENT_FIRST);
        if (metaCsrf == null) {
            throw new IOException("login appears to have failed: no _csrf meta tag on / "
                    + "(status " + home.statusCode() + ") — check credentials");
        }
        this.csrfToken = metaCsrf;
    }

    /**
     * Executes a FlexibleSearch statement and returns the raw result, whether or
     * not HAC reported an error. Inspect {@link FlexResult#hasError()} to decide
     * how to proceed.
     *
     * @param query the FlexibleSearch statement
     * @param maxCount the maximum number of rows to return
     * @return the parsed result (possibly carrying an {@link FlexResult#exception()})
     * @throws IOException if the request fails or the response is not parseable
     * @throws InterruptedException if the calling thread is interrupted
     * @throws IllegalStateException if {@link #login()} has not been called
     */
    public FlexResult tryExecute(String query, int maxCount)
            throws IOException, InterruptedException {
        if (csrfToken == null) {
            throw new IllegalStateException("not authenticated; call login() first");
        }
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        Map<String, String> form = new LinkedHashMap<>();
        form.put("flexibleSearchQuery", query);
        form.put("maxCount", Integer.toString(maxCount));
        form.put("user", config.user());
        form.put("locale", "en");
        form.put("dataSource", "master");
        form.put("commit", "false");

        HttpResponse<String> resp = http.send(
                postForm(config.baseUrl() + "/console/flexsearch/execute", form, csrfToken),
                HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 400) {
            throw new IOException("FlexibleSearch execute failed with status "
                    + resp.statusCode() + ": " + truncate(resp.body()));
        }
        try {
            return FlexResult.fromJson(resp.body());
        } catch (RuntimeException e) {
            throw new IOException("could not parse FlexibleSearch response: "
                    + truncate(resp.body()), e);
        }
    }

    /**
     * Executes a FlexibleSearch statement, throwing {@link FlexQueryException}
     * when HAC reports a server-side error.
     *
     * @param query the FlexibleSearch statement
     * @param maxCount the maximum number of rows to return
     * @return the successful result
     * @throws FlexQueryException if HAC reports an exception for the query
     * @throws IOException if the request fails or the response is not parseable
     * @throws InterruptedException if the calling thread is interrupted
     */
    public FlexResult execute(String query, int maxCount)
            throws IOException, InterruptedException {
        FlexResult result = tryExecute(query, maxCount);
        if (result.hasError()) {
            throw new FlexQueryException(result);
        }
        return result;
    }

    /**
     * Convenience overload that builds the query via {@link QueryBuilder} and
     * executes it, throwing on a server-side error.
     *
     * @param qb a configured builder
     * @param maxCount the maximum number of rows to return
     * @return the successful result
     * @throws FlexQueryException if HAC reports an exception for the query
     * @throws IOException if the request fails or the response is not parseable
     * @throws InterruptedException if the calling thread is interrupted
     */
    public FlexResult execute(QueryBuilder qb, int maxCount)
            throws IOException, InterruptedException {
        if (qb == null) {
            throw new IllegalArgumentException("query builder must not be null");
        }
        return execute(qb.build(), maxCount);
    }

    // ---------------------------------------------------------------------
    // HTTP helpers
    // ---------------------------------------------------------------------

    private HttpRequest get(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("Accept", "text/html,application/xhtml+xml,application/json")
                .GET()
                .build();
    }

    private HttpRequest postForm(String url, Map<String, String> form, String csrf) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json, text/html, */*")
                .POST(HttpRequest.BodyPublishers.ofString(encodeForm(form)));
        if (csrf != null) {
            b.header("X-CSRF-TOKEN", csrf);
        }
        return b.build();
    }

    private static String encodeForm(Map<String, String> form) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : form.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private static String firstMatch(String body, Pattern... patterns) {
        if (body == null) {
            return null;
        }
        for (Pattern p : patterns) {
            Matcher m = p.matcher(body);
            if (m.find()) {
                return m.group(1);
            }
        }
        return null;
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 500 ? s : s.substring(0, 500) + "...";
    }

    /**
     * Builds an all-trusting {@link SSLContext} for local self-signed instances.
     * Deliberately trusts every certificate; only used when
     * {@link HacConfig#insecureTls()} is {@code true}.
     */
    private static SSLContext trustAllContext() {
        try {
            TrustManager[] trustAll = new TrustManager[] {
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }
            };
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, trustAll, new SecureRandom());
            return ctx;
        } catch (Exception e) {
            throw new IllegalStateException("could not create insecure SSLContext", e);
        }
    }
}
