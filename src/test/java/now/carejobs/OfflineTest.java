package now.carejobs;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Offline tests: no framework needed, nothing leaves the machine. The HTTP call
 * is replaced by a fake transport, and some answers are real ones recorded from
 * the live API (in src/test/resources/fixtures).
 *
 * <p>Run: {@code java -cp <classes>:<test classes> now.carejobs.OfflineTest [fixtures dir]}
 */
public final class OfflineTest {

    private static int failures;
    private static int passes;
    private static Path fixtures = Paths.get("src/test/resources/fixtures");

    private static void check(String name, boolean ok) {
        System.out.println((ok ? "ok   " : "FAIL ") + name);
        if (ok) {
            passes++;
        } else {
            failures++;
        }
    }

    /** What the fake transport saw. */
    private static final class Sent {
        URI uri;
        String raw;
        Map<String, Object> payload;
        Map<String, String> headers;
        Duration timeout;
    }

    private static String answer(Map<String, Object> structured, String text) {
        Map<String, Object> part = new LinkedHashMap<>();
        part.put("type", "text");
        part.put("text", text);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", Arrays.asList(part));
        result.put("structuredContent", structured);
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("jsonrpc", "2.0");
        message.put("id", 1L);
        message.put("result", result);
        return Json.write(message);
    }

    private static Map<String, Object> map(Object... keysAndValues) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            m.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return m;
    }

    private static CareJobsClient fake(Sent sent, int status, String contentType, String body) {
        return CareJobsClient.builder().transport((uri, raw, headers, timeout) -> {
            sent.uri = uri;
            sent.raw = raw;
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) Json.parse(raw);
            sent.payload = payload;
            sent.headers = headers;
            sent.timeout = timeout;
            return new HttpReply(status, contentType, body);
        }).build();
    }

    private static CareJobsClient fixture(String name) throws IOException {
        String body = new String(Files.readAllBytes(fixtures.resolve(name)), StandardCharsets.UTF_8);
        return CareJobsClient.builder().transport((uri, raw, headers, timeout) -> new HttpReply(200, "application/json", body)).build();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> params(Sent sent) {
        return (Map<String, Object>) sent.payload.get("params");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> arguments(Sent sent) {
        return (Map<String, Object>) params(sent).get("arguments");
    }

    private static String reason(Runnable call) {
        try {
            call.run();
        } catch (CareJobsException e) {
            return e.reason().value();
        }
        return "none";
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            fixtures = Paths.get(args[0]);
        }

        // a plain JSON answer and the request that was sent
        Sent sent = new Sent();
        CareJobsClient client = fake(sent, 200, "application/json",
                answer(map("total", 3L, "jobs", Arrays.asList(map("title", "RN"))), "three jobs"));
        Result res = client.searchJobs(new SearchParams().query("ICU").kind(Kind.NURSES).state("TX").limit(5));
        check("search url", sent.uri.toString().equals("https://carejobs.now/api/mcp"));
        check("search method", "tools/call".equals(sent.payload.get("method")) && "search_healthcare_jobs".equals(params(sent).get("name")));
        check("search arguments", arguments(sent).equals(map("query", "ICU", "kind", "nurses", "state", "TX", "limit", 5L)));
        check("search argument order", sent.raw.contains("\"arguments\":{\"query\":\"ICU\",\"kind\":\"nurses\",\"state\":\"TX\",\"limit\":5}"));
        check("jsonrpc envelope", "2.0".equals(sent.payload.get("jsonrpc")) && sent.payload.get("id") instanceof Long);
        check("search result data", res.getLong("total") == 3L && "RN".equals(res.getMaps("jobs").get(0).get("title")));
        check("search result text", res.text().equals("three jobs") && res.toString().equals("three jobs"));
        check("headers", "application/json".equals(sent.headers.get("Content-Type"))
                && "application/json, text/event-stream".equals(sent.headers.get("Accept"))
                && ("carejobs-java/" + CareJobsClient.VERSION).equals(sent.headers.get("User-Agent")));
        check("default timeout", sent.timeout.equals(Duration.ofSeconds(60)));
        check("result is read-only", reason(() -> {
            try {
                res.data().put("x", 1L);
            } catch (UnsupportedOperationException e) {
                throw new CareJobsException("read-only", CareJobsException.Reason.PROTOCOL, null);
            }
        }).equals("protocol"));

        // remote and minimum pay
        client.searchJobs(new SearchParams().remote(true).minHourlyUsd(50).sort(SearchParams.SORT_HIGHEST_PAY));
        check("remote and pay arguments", arguments(sent).equals(map("remote", true, "min_hourly_usd", 50L, "sort", "highest_pay")));
        client.searchJobs(new SearchParams().remote(false));
        check("remote false is left out", arguments(sent).isEmpty());
        client.searchJobs("travel");
        check("search by words", arguments(sent).equals(map("query", "travel")));

        // ids go up
        long first = (Long) sent.payload.get("id");
        client.jobMarket();
        check("ids go up", (Long) sent.payload.get("id") == first + 1);

        // no arguments encode as an empty JSON object, not a list
        check("empty arguments are an object", sent.raw.contains("\"arguments\":{}"));
        check("market tool", "get_healthcare_job_market".equals(params(sent).get("name")));

        // pay
        client.payByKind("physicians");
        check("pay arguments", "get_healthcare_pay".equals(params(sent).get("name")) && arguments(sent).equals(map("kind", "physicians")));
        client.pay(Kind.NURSES, "kaiser-permanente");
        check("pay kind and employer", arguments(sent).equals(map("kind", "nurses", "employer", "kaiser-permanente")));
        client.payByEmployer("cvs-health");
        check("pay by employer", arguments(sent).equals(map("employer", "cvs-health")));
        client.pay();
        check("pay overview", arguments(sent).isEmpty());

        // states and employers
        client.states("OH", Kind.NURSING_ASSISTANTS);
        check("states arguments", "get_healthcare_jobs_by_state".equals(params(sent).get("name"))
                && arguments(sent).equals(map("state", "OH", "kind", "nursing-assistants")));
        client.jobsByState(new StateParams().state("TX").kind("pharmacy").limit(3));
        check("state params", arguments(sent).equals(map("state", "TX", "kind", "pharmacy", "limit", 3L)));
        client.employers(new EmployerParams().query("Kaiser").limit(3));
        check("employers arguments", "list_healthcare_employers".equals(params(sent).get("name"))
                && arguments(sent).equals(map("query", "Kaiser", "limit", 3L)));
        client.employers(new EmployerParams().type(EmployerParams.TYPE_AI_PROGRAM).care("home-care").state("CA").kind(Kind.PHARMACY));
        check("employer filters", arguments(sent).equals(map("type", "ai-program", "care", "home-care", "state", "CA", "kind", "pharmacy")));
        client.employer("cvs-health");
        check("employer arguments", "get_healthcare_employer".equals(params(sent).get("name")) && arguments(sent).equals(map("employer", "cvs-health")));

        // callTool drops nulls and sends a Kind as its slug
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("kind", Kind.AI_TRAINING);
        raw.put("employer", null);
        client.callTool("get_healthcare_pay", raw);
        check("callTool cleans arguments", arguments(sent).equals(map("kind", "ai-training")));

        // kinds
        List<String> kinds = client.kinds();
        check("17 kinds", kinds.size() == 17 && kinds.get(0).equals("nurses") && kinds.contains("ai-training"));
        check("kind from value", Kind.fromValue("emts-paramedics") == Kind.EMTS_PARAMEDICS && Kind.NURSES.toString().equals("nurses"));

        // options
        Sent opts = new Sent();
        CareJobsClient custom = CareJobsClient.builder().baseUrl("http://localhost:8080/").timeout(Duration.ofSeconds(5)).userAgent("my-app/1.0")
                .transport((uri, raw2, headers, timeout) -> {
                    opts.uri = uri;
                    opts.headers = headers;
                    opts.timeout = timeout;
                    return new HttpReply(200, "application/json", answer(map(), ""));
                }).build();
        custom.jobMarket();
        check("base url, timeout and user agent", opts.uri.toString().equals("http://localhost:8080/api/mcp")
                && opts.timeout.equals(Duration.ofSeconds(5)) && "my-app/1.0".equals(opts.headers.get("User-Agent")));

        // a text/event-stream answer
        CareJobsClient sse = fake(new Sent(), 200, "text/event-stream",
                "event: message\r\ndata: " + answer(map("open_jobs", 9L), "nine") + "\r\n\r\n");
        Result market = sse.jobMarket();
        check("event stream", market.getLong("open_jobs") == 9L && market.text().equals("nine"));
        CareJobsClient sseNoId = fake(new Sent(), 200, "text/event-stream; charset=utf-8",
                ": ping\n\nevent: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":99,\"result\":{\"content\":[],\"structuredContent\":{\"a\":1}}}\n\n");
        check("event stream fallback", sseNoId.jobMarket().getLong("a") == 1L);
        check("empty event stream", reason(() -> fake(new Sent(), 200, "text/event-stream", ": nothing\n\n").jobMarket()).equals("protocol"));

        // failures
        String toolErr = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"isError\":true,\"content\":[{\"type\":\"text\",\"text\":\"bad state\"}]}}";
        try {
            fake(new Sent(), 200, "application/json", toolErr).jobsByState("ZZ");
            check("tool error", false);
        } catch (CareJobsException e) {
            check("tool error", e.reason() == CareJobsException.Reason.TOOL && e.getMessage().equals("bad state")
                    && "get_healthcare_jobs_by_state".equals(e.tool()));
        }
        try {
            fake(new Sent(), 200, "application/json", "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32601,\"message\":\"no such method\"}}").jobMarket();
            check("rpc error", false);
        } catch (CareJobsException e) {
            check("rpc error", e.reason() == CareJobsException.Reason.RPC && e.rpcCode() == -32601 && e.getMessage().equals("no such method"));
        }
        try {
            fake(new Sent(), 503, "text/plain", "down").jobMarket();
            check("http error", false);
        } catch (CareJobsException e) {
            check("http error", e.reason() == CareJobsException.Reason.HTTP && e.status() == 503 && e.getMessage().equals("HTTP 503: down"));
        }
        try {
            fake(new Sent(), 400, "application/json", "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32700,\"message\":\"Parse error\"}}").jobMarket();
            check("http error with a JSON-RPC body", false);
        } catch (CareJobsException e) {
            check("http error with a JSON-RPC body", e.reason() == CareJobsException.Reason.HTTP && e.getMessage().equals("HTTP 400: Parse error"));
        }
        check("protocol error", reason(() -> fake(new Sent(), 200, "text/html", "<html>").jobMarket()).equals("protocol"));
        check("missing structured content", reason(() -> fake(new Sent(), 200, "application/json",
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"content\":[]}}").jobMarket()).equals("protocol"));
        check("missing result", reason(() -> fake(new Sent(), 200, "application/json", "{\"jsonrpc\":\"2.0\",\"id\":1}").jobMarket()).equals("protocol"));
        check("timeout", reason(() -> CareJobsClient.builder().transport((u, b, h, t) -> {
            throw new HttpTimeoutException("slow");
        }).build().jobMarket()).equals("timeout"));
        check("network", reason(() -> CareJobsClient.builder().transport((u, b, h, t) -> {
            throw new java.net.ConnectException("refused");
        }).build().jobMarket()).equals("network"));
        check("interrupted", reason(() -> CareJobsClient.builder().transport((u, b, h, t) -> {
            throw new InterruptedException();
        }).build().jobMarket()).equals("canceled") && Thread.interrupted());

        // bad settings
        for (String bad : new String[] {"not a url", "ftp://example.com", "https://"}) {
            try {
                CareJobsClient.builder().baseUrl(bad).build();
                check("bad base url " + bad, false);
            } catch (IllegalArgumentException e) {
                check("bad base url " + bad, true);
            }
        }
        try {
            CareJobsClient.builder().timeout(Duration.ZERO);
            check("zero timeout", false);
        } catch (IllegalArgumentException e) {
            check("zero timeout", true);
        }

        // recorded answers from the live API
        Result search = fixture("search.json").searchJobs(new SearchParams().kind(Kind.NURSES).state("TX").limit(3));
        check("recorded search", search.getLong("total") > 0 && search.getMaps("jobs").size() == 3
                && search.getMaps("jobs").get(0).get("url").toString().startsWith("https://carejobs.now/jobs/")
                && search.text().contains("open healthcare jobs match"));
        Result payKind = fixture("pay_kind.json").payByKind(Kind.NURSES);
        check("recorded pay by kind", payKind.getMap("pay").get("median") instanceof Number && !payKind.getMaps("best_paying").isEmpty());
        Result payOverview = fixture("pay_overview.json").pay();
        check("recorded pay overview", !payOverview.getMaps("pay_by_kind").isEmpty());
        Result unmeasured = fixture("pay_employer_unmeasured.json").payByEmployer("x");
        check("recorded unmeasured pay is null", unmeasured.has("pay") && unmeasured.get("pay") == null);
        Result market2 = fixture("market.json").jobMarket();
        check("recorded market", market2.getLong("open_jobs") > 0 && !market2.getMaps("open_by_kind").isEmpty());
        Result state = fixture("state_detail.json").states("OH");
        check("recorded state", state.getString("state") != null && state.getLong("open_jobs") > 0);
        Result overview = fixture("states_overview.json").states();
        check("recorded states overview", !overview.getMaps("states").isEmpty());
        Result employers = fixture("employers.json").employers("Kaiser");
        check("recorded employers", !employers.getMaps("entries").isEmpty() && employers.getMaps("entries").get(0).get("slug") instanceof String);
        Result employer = fixture("employer.json").employer("kaiser-permanente");
        check("recorded employer", employer.getString("name") != null && employer.toJson().startsWith("{"));
        List<Map<String, Object>> tools = fixture("tools_list.json").tools();
        check("recorded tools", tools.size() == 6 && "search_healthcare_jobs".equals(tools.get(0).get("name")));
        @SuppressWarnings("unchecked")
        Map<String, Object> schema = (Map<String, Object>) ((Map<String, Object>) tools.get(0).get("inputSchema")).get("properties");
        @SuppressWarnings("unchecked")
        List<Object> kindEnum = (List<Object>) ((Map<String, Object>) schema.get("kind")).get("enum");
        check("Kind matches the recorded schema", kindEnum.equals(Kind.slugs()));
        try {
            fixture("error_tool.json").employer("no-such-employer-xyz");
            check("recorded tool error", false);
        } catch (CareJobsException e) {
            check("recorded tool error", e.reason() == CareJobsException.Reason.TOOL && e.getMessage().startsWith("Unknown employer"));
        }
        check("recorded rpc error", reason(() -> {
            try {
                fixture("error_rpc.json").jobMarket();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).equals("rpc"));

        // the JSON reader and writer
        check("json unicode", "caf\u00e9 \uD83D\uDE00".equals(Json.parse("\"caf\\u00e9 \\ud83d\\ude00\"")));
        check("json numbers", Long.valueOf(-12).equals(Json.parse("-12")) && Double.valueOf(1.5e3).equals(Json.parse("1.5E3"))
                && Double.valueOf(0.25).equals(Json.parse("0.25")) && Json.parse("12345678901234567890") instanceof Double);
        check("json literals", Boolean.TRUE.equals(Json.parse(" true ")) && Json.parse("null") == null);
        check("json nested", Json.parse("{\"a\":[1,{\"b\":\"c\"}],\"d\":{}}").equals(map("a", Arrays.asList(1L, map("b", "c")), "d", map())));
        for (String bad : new String[] {"", "{", "[1,]", "{\"a\" 1}", "01", "1.", "\"\\x\"", "tru", "{} x", "\"a\nb\""}) {
            try {
                Json.parse(bad);
                check("json rejects " + bad.replace("\n", "\\n"), false);
            } catch (IllegalArgumentException e) {
                check("json rejects " + bad.replace("\n", "\\n"), true);
            }
        }
        check("json writes escapes", Json.write("a\"b\\c\n\u0001\u00e9").equals("\"a\\\"b\\\\c\\n\\u0001\u00e9\""));
        check("json writes numbers", Json.write(Arrays.asList(1, 2L, 2.5, 3.0)).equals("[1,2,2.5,3]"));
        check("json round trip", Json.parse(Json.write(map("x", Arrays.asList(true, null, "y")))).equals(map("x", Arrays.asList(true, null, "y"))));
        try {
            Json.write(Double.NaN);
            check("json refuses NaN", false);
        } catch (IllegalArgumentException e) {
            check("json refuses NaN", true);
        }

        System.out.println(failures == 0 ? "all " + passes + " passed" : failures + " failed, " + passes + " passed");
        System.exit(failures == 0 ? 0 : 1);
    }
}
