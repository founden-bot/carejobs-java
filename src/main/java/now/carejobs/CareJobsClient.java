package now.carejobs;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Client for the CareJobs.Now healthcare jobs API, a remote MCP server that
 * needs no key. Every method returns a {@link Result} and every failure throws a
 * {@link CareJobsException}. A client keeps no state between calls (apart from a
 * request counter), so one instance can be shared, also between threads.
 *
 * <pre>{@code
 * CareJobsClient client = new CareJobsClient();
 * Result jobs = client.searchJobs(new SearchParams().query("ICU").kind(Kind.NURSES).state("TX").limit(5));
 * System.out.println(jobs.getLong("total") + " jobs match");
 * }</pre>
 *
 * <p>Data from CareJobs.Now (https://carejobs.now), CC BY 4.0.
 */
public final class CareJobsClient {

    /** The version of this library. */
    public static final String VERSION = "1.0.0";

    /** The default host; the MCP endpoint is {@code <baseUrl>/api/mcp}. */
    public static final String DEFAULT_BASE_URL = "https://carejobs.now";

    /**
     * The default time a request may take. The server answers a query it has not
     * seen before in up to about 25 seconds (its database may be waking up), then
     * repeats of it in a fraction of one.
     */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

    /** The largest real answer (the whole job market) is about 135 KB. */
    private static final int MAX_RESPONSE_BYTES = 16 * 1024 * 1024;

    private final URI endpoint;
    private final Duration timeout;
    private final String userAgent;
    private final Transport transport;
    private final AtomicLong nextId = new AtomicLong();

    /** Creates a client with the defaults. */
    public CareJobsClient() {
        this(builder());
    }

    private CareJobsClient(Builder builder) {
        String base = builder.baseUrl;
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        URI uri;
        try {
            uri = new URI(base + "/api/mcp");
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("baseUrl must be an http:// or https:// address, not \"" + builder.baseUrl + "\"", e);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!(scheme.equals("http") || scheme.equals("https")) || uri.getHost() == null) {
            throw new IllegalArgumentException("baseUrl must be an http:// or https:// address, not \"" + builder.baseUrl + "\"");
        }
        this.endpoint = uri;
        this.timeout = builder.timeout;
        this.userAgent = builder.userAgent != null ? builder.userAgent : "carejobs-java/" + VERSION;
        this.transport = builder.transport != null ? builder.transport : new JdkTransport(builder.timeout);
    }

    /**
     * Starts the settings of a client.
     *
     * @return a builder with the defaults
     */
    public static Builder builder() {
        return new Builder();
    }

    /** The settings of a {@link CareJobsClient}. */
    public static final class Builder {
        private String baseUrl = DEFAULT_BASE_URL;
        private Duration timeout = DEFAULT_TIMEOUT;
        private String userAgent;
        private Transport transport;

        private Builder() {
        }

        /**
         * The host. Default {@value CareJobsClient#DEFAULT_BASE_URL}.
         *
         * @param baseUrl an http:// or https:// address
         * @return this builder
         */
        public Builder baseUrl(String baseUrl) {
            if (baseUrl == null) {
                throw new IllegalArgumentException("baseUrl must not be null");
            }
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * How long a request may take. Default 60 seconds.
         *
         * @param timeout a positive duration
         * @return this builder
         */
        public Builder timeout(Duration timeout) {
            if (timeout == null || timeout.isZero() || timeout.isNegative()) {
                throw new IllegalArgumentException("timeout must be a duration above 0");
            }
            this.timeout = timeout;
            return this;
        }

        /**
         * The User-Agent header. Default {@code "carejobs-java/<version>"}.
         *
         * @param userAgent the header value
         * @return this builder
         */
        public Builder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        /**
         * Replaces the HTTP call, for tests or another HTTP stack.
         *
         * @param transport the transport, or null for the default
         * @return this builder
         */
        public Builder transport(Transport transport) {
            this.transport = transport;
            return this;
        }

        /**
         * Creates the client.
         *
         * @return the client
         * @throws IllegalArgumentException when the base URL is not an http:// or https:// address
         */
        public CareJobsClient build() {
            return new CareJobsClient(this);
        }
    }

    // -- the six tools -----------------------------------------------------------------

    /**
     * Searches the open healthcare jobs. The data has {@code total} (every
     * match), {@code pay}, {@code jobs} (the ones returned) and {@code more}.
     *
     * @param params the filters
     * @return the answer
     */
    public Result searchJobs(SearchParams params) {
        return callTool("search_healthcare_jobs", params == null ? Collections.emptyMap() : params.arguments());
    }

    /**
     * Searches the open healthcare jobs by words only.
     *
     * @param query words every job must carry, for example {@code "ICU"}
     * @return the answer
     */
    public Result searchJobs(String query) {
        return searchJobs(new SearchParams().query(query));
    }

    /**
     * What every kind of healthcare job posts as hourly pay, measured from open
     * jobs (before shift differentials and bonuses).
     *
     * @return the answer
     */
    public Result pay() {
        return callTool("get_healthcare_pay", Collections.emptyMap());
    }

    /**
     * Posted hourly pay. With a kind: that job's pay and the employers posting
     * the most for it. With an employer: its pay overall and by kind. With both:
     * that employer's pay for that kind. Either may be null.
     *
     * @param kind     a kind of job, or null
     * @param employer an employer slug or name, or null
     * @return the answer
     */
    public Result pay(Kind kind, String employer) {
        return pay(kind == null ? null : kind.value(), employer);
    }

    /**
     * {@link #pay(Kind, String)} with the kind as a slug.
     *
     * @param kind     a kind slug such as {@code "nurses"}, or null
     * @param employer an employer slug or name, or null
     * @return the answer
     */
    public Result pay(String kind, String employer) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("kind", kind);
        args.put("employer", employer);
        return callTool("get_healthcare_pay", args);
    }

    /**
     * The pay of one kind of job and the employers posting the most for it.
     *
     * @param kind the kind
     * @return the answer
     */
    public Result payByKind(Kind kind) {
        return pay(kind, null);
    }

    /**
     * {@link #payByKind(Kind)} with the kind as a slug.
     *
     * @param kind a slug such as {@code "physicians"}
     * @return the answer
     */
    public Result payByKind(String kind) {
        return pay(kind, null);
    }

    /**
     * The pay one employer posts, overall and by kind of job.
     *
     * @param employer an employer slug ({@code "kaiser-permanente"}) or name
     * @return the answer
     */
    public Result payByEmployer(String employer) {
        return pay((String) null, employer);
    }

    /**
     * How many healthcare jobs are open, how many were first listed in the last
     * 7 days, and the open jobs by kind of job, by state and by employer.
     *
     * @return the answer
     */
    public Result jobMarket() {
        return callTool("get_healthcare_job_market", Collections.emptyMap());
    }

    /**
     * Open jobs in every US state.
     *
     * @return the answer
     */
    public Result jobsByState() {
        return jobsByState(new StateParams());
    }

    /**
     * One state's open jobs by kind, pay, towns, employers and newest jobs.
     *
     * @param state {@code "OH"} or {@code "Ohio"}
     * @return the answer
     */
    public Result jobsByState(String state) {
        return jobsByState(new StateParams().state(state));
    }

    /**
     * One kind of job in one state.
     *
     * @param state {@code "OH"} or {@code "Ohio"}
     * @param kind  the kind
     * @return the answer
     */
    public Result jobsByState(String state, Kind kind) {
        return jobsByState(new StateParams().state(state).kind(kind));
    }

    /**
     * Open jobs by US state, with every filter.
     *
     * @param params the filters
     * @return the answer
     */
    public Result jobsByState(StateParams params) {
        return callTool("get_healthcare_jobs_by_state", params == null ? Collections.emptyMap() : params.arguments());
    }

    /**
     * Alias of {@link #jobsByState()}.
     *
     * @return the answer
     */
    public Result states() {
        return jobsByState();
    }

    /**
     * Alias of {@link #jobsByState(String)}.
     *
     * @param state {@code "OH"} or {@code "Ohio"}
     * @return the answer
     */
    public Result states(String state) {
        return jobsByState(state);
    }

    /**
     * Alias of {@link #jobsByState(String, Kind)}.
     *
     * @param state {@code "OH"} or {@code "Ohio"}
     * @param kind  the kind
     * @return the answer
     */
    public Result states(String state, Kind kind) {
        return jobsByState(state, kind);
    }

    /**
     * The employers (and AI-training programs and job boards) the site reads,
     * most open jobs first.
     *
     * @return the answer
     */
    public Result employers() {
        return employers(new EmployerParams());
    }

    /**
     * The employers whose name contains these words.
     *
     * @param query part of the name, for example {@code "Kaiser"}
     * @return the answer
     */
    public Result employers(String query) {
        return employers(new EmployerParams().query(query));
    }

    /**
     * The employers, with every filter.
     *
     * @param params the filters
     * @return the answer
     */
    public Result employers(EmployerParams params) {
        return callTool("list_healthcare_employers", params == null ? Collections.emptyMap() : params.arguments());
    }

    /**
     * One employer, AI-training program or job board: what it is, where it hires,
     * its open jobs by kind, the pay it posts, its newest and best-paid jobs, and
     * links to its pages.
     *
     * @param slug a slug ({@code "kaiser-permanente"}) or name
     * @return the answer
     */
    public Result employer(String slug) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("employer", slug);
        return callTool("get_healthcare_employer", args);
    }

    /**
     * The kinds of job the API knows, as slugs ({@code "nurses"}, {@code "physicians"}, ...).
     *
     * @return the slugs, read-only
     */
    public List<String> kinds() {
        return Kind.slugs();
    }

    // -- low level ---------------------------------------------------------------------

    /**
     * The tools the server offers, with their input schemas.
     *
     * @return the tools as read-only maps
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> tools() {
        Map<String, Object> result = rpc("tools/list", Collections.emptyMap(), null);
        Object tools = result.get("tools");
        if (!(tools instanceof List)) {
            throw new CareJobsException("tools/list answered without a tools list", CareJobsException.Reason.PROTOCOL, null);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object tool : (List<Object>) tools) {
            if (!(tool instanceof Map)) {
                throw new CareJobsException("tools/list answered with a tool that is not an object", CareJobsException.Reason.PROTOCOL, null);
            }
            out.add((Map<String, Object>) tool);
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * Calls any tool by name, for a tool added after this version. Arguments keep
     * the API's names; null values are left out and a {@link Kind} is sent as its
     * slug. The methods above use this.
     *
     * @param name      the tool name, for example {@code "get_healthcare_pay"}
     * @param arguments the arguments, or null for none
     * @return the answer
     */
    public Result callTool(String name, Map<String, ?> arguments) {
        Map<String, Object> clean = new LinkedHashMap<>();
        if (arguments != null) {
            for (Map.Entry<String, ?> entry : arguments.entrySet()) {
                Object value = entry.getValue();
                if (value == null) {
                    continue;
                }
                clean.put(entry.getKey(), value instanceof Kind ? ((Kind) value).value() : value);
            }
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", name);
        params.put("arguments", clean);

        Map<String, Object> raw = rpc("tools/call", params, name);

        StringBuilder text = new StringBuilder();
        Object content = raw.get("content");
        if (content instanceof List) {
            for (Object part : (List<?>) content) {
                if (part instanceof Map) {
                    Map<?, ?> p = (Map<?, ?>) part;
                    if ("text".equals(p.get("type")) && p.get("text") instanceof String) {
                        if (text.length() > 0) {
                            text.append('\n');
                        }
                        text.append((String) p.get("text"));
                    }
                }
            }
        }

        if (Boolean.TRUE.equals(raw.get("isError"))) {
            throw new CareJobsException(text.length() > 0 ? text.toString() : name + " failed", CareJobsException.Reason.TOOL, name);
        }
        Object data = raw.get("structuredContent");
        if (!(data instanceof Map)) {
            throw new CareJobsException(name + " answered without structured content", CareJobsException.Reason.PROTOCOL, name);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) data;
        return new Result(map, text.toString());
    }

    /** Sends one JSON-RPC request and returns the result of its answer. */
    private Map<String, Object> rpc(String method, Map<String, Object> params, String tool) {
        long id = nextId.incrementAndGet();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("id", id);
        request.put("method", method);
        request.put("params", params);
        String payload;
        try {
            payload = Json.write(request);
        } catch (IllegalArgumentException e) {
            throw new CareJobsException("The arguments cannot be encoded as JSON: " + e.getMessage(), CareJobsException.Reason.PROTOCOL, tool, e);
        }

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json, text/event-stream");
        headers.put("User-Agent", userAgent);

        HttpReply reply;
        try {
            reply = transport.post(endpoint, payload, Collections.unmodifiableMap(headers), timeout);
        } catch (HttpTimeoutException e) {
            throw new CareJobsException("No answer from " + endpoint + " within " + timeout.toMillis() / 1000.0 + " s", CareJobsException.Reason.TIMEOUT, tool, e);
        } catch (IOException e) {
            throw new CareJobsException("Could not reach " + endpoint + ": " + e, CareJobsException.Reason.NETWORK, tool, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CareJobsException("Interrupted while waiting for " + endpoint, CareJobsException.Reason.CANCELED, tool, e);
        }
        if (reply == null) {
            throw new CareJobsException("The transport returned no answer", CareJobsException.Reason.PROTOCOL, tool);
        }

        if (reply.status() < 200 || reply.status() > 299) {
            throw new CareJobsException(httpErrorDetail(reply, id), CareJobsException.Reason.HTTP, tool, reply.status(), null, null);
        }

        Map<String, Object> message = parseMessage(reply.body(), reply.contentType(), id, tool);
        if (message.get("error") != null) {
            Object error = message.get("error");
            if (!(error instanceof Map)) {
                throw new CareJobsException("Malformed JSON-RPC error", CareJobsException.Reason.PROTOCOL, tool);
            }
            Map<?, ?> e = (Map<?, ?>) error;
            String text = e.get("message") instanceof String && !((String) e.get("message")).isEmpty()
                    ? (String) e.get("message") : "JSON-RPC error";
            Integer code = e.get("code") instanceof Long ? Integer.valueOf(((Long) e.get("code")).intValue()) : null;
            throw new CareJobsException(text, CareJobsException.Reason.RPC, tool, null, code, null);
        }
        Object result = message.get("result");
        if (!(result instanceof Map)) {
            throw new CareJobsException("The answer held no result", CareJobsException.Reason.PROTOCOL, tool);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) result;
        return map;
    }

    private String httpErrorDetail(HttpReply reply, long id) {
        String detail = "HTTP " + reply.status();
        try {
            Object error = parseMessage(reply.body(), reply.contentType(), id, null).get("error");
            if (error instanceof Map) {
                Object message = ((Map<?, ?>) error).get("message");
                if (message instanceof String && !((String) message).isEmpty()) {
                    return detail + ": " + message;
                }
            }
        } catch (CareJobsException notJsonRpc) {
            // not a JSON-RPC body: fall through to the raw text
        }
        String text = reply.body().trim();
        if (text.isEmpty()) {
            return detail;
        }
        return detail + ": " + (text.length() > 200 ? text.substring(0, 200) : text);
    }

    /** Reads the JSON-RPC message out of a plain JSON body or a text/event-stream body. */
    private static Map<String, Object> parseMessage(String body, String contentType, long id, String tool) {
        if (!contentType.toLowerCase(Locale.ROOT).contains("text/event-stream")) {
            return decodeObject(body, tool);
        }
        Map<String, Object> fallback = null;
        for (String event : body.replace("\r\n", "\n").split("\n\n")) {
            StringBuilder data = new StringBuilder();
            boolean any = false;
            for (String line : event.split("\n")) {
                if (line.startsWith("data:")) {
                    if (any) {
                        data.append('\n');
                    }
                    any = true;
                    data.append(stripLeadingSpaces(line.substring(5)));
                }
            }
            if (!any) {
                continue;
            }
            Map<String, Object> message;
            try {
                message = decodeObject(data.toString(), tool);
            } catch (CareJobsException skip) {
                continue;
            }
            Object messageId = message.get("id");
            if (messageId instanceof Long && (Long) messageId == id) {
                return message;
            }
            if (fallback == null && (message.containsKey("result") || message.containsKey("error"))) {
                fallback = message;
            }
        }
        if (fallback != null) {
            return fallback;
        }
        throw new CareJobsException("The event stream held no JSON-RPC answer", CareJobsException.Reason.PROTOCOL, tool);
    }

    private static String stripLeadingSpaces(String s) {
        int i = 0;
        while (i < s.length() && s.charAt(i) == ' ') {
            i++;
        }
        return s.substring(i);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> decodeObject(String json, String tool) {
        Object value;
        try {
            value = Json.parse(json);
        } catch (IllegalArgumentException e) {
            throw new CareJobsException("The server did not answer with JSON", CareJobsException.Reason.PROTOCOL, tool, e);
        }
        if (!(value instanceof Map)) {
            throw new CareJobsException("The server did not answer with a JSON-RPC object", CareJobsException.Reason.PROTOCOL, tool);
        }
        return (Map<String, Object>) value;
    }

    // -- HTTP --------------------------------------------------------------------------

    /** The default transport, on the JDK's HttpClient. */
    private static final class JdkTransport implements Transport {
        private final HttpClient http;

        JdkTransport(Duration timeout) {
            Duration connect = timeout.compareTo(Duration.ofSeconds(30)) < 0 ? timeout : Duration.ofSeconds(30);
            this.http = HttpClient.newBuilder()
                    .connectTimeout(connect)
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
        }

        @Override
        public HttpReply post(URI uri, String body, Map<String, String> headers, Duration timeout) throws IOException, InterruptedException {
            HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                    .timeout(timeout)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            for (Map.Entry<String, String> header : headers.entrySet()) {
                request.header(header.getKey(), header.getValue());
            }
            HttpResponse<InputStream> response = http.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
            byte[] bytes;
            try (InputStream in = response.body()) {
                bytes = readLimited(in);
            }
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            return new HttpReply(response.statusCode(), contentType, decodeUtf8(bytes));
        }

        private static byte[] readLimited(InputStream in) throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int total = 0;
            int n;
            while ((n = in.read(buffer)) != -1) {
                total += n;
                if (total > MAX_RESPONSE_BYTES) {
                    throw new CareJobsException("The answer is larger than " + MAX_RESPONSE_BYTES + " bytes", CareJobsException.Reason.PROTOCOL, null);
                }
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        }

        private static String decodeUtf8(byte[] bytes) {
            try {
                return StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPLACE)
                        .onUnmappableCharacter(CodingErrorAction.REPLACE)
                        .decode(ByteBuffer.wrap(bytes))
                        .toString();
            } catch (CharacterCodingException cannotHappenWithReplace) {
                return new String(bytes, StandardCharsets.UTF_8);
            }
        }
    }
}
