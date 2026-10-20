package now.carejobs;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Calls the live API at https://carejobs.now once per method and checks the
 * shape of each answer. Needs the network; the first call of a kind can take
 * up to about 25 seconds.
 *
 * <p>Run: {@code java -cp <classes>:<test classes> now.carejobs.LiveTest}
 */
public final class LiveTest {

    private static int failures;
    private static int passes;

    private static void check(String name, boolean ok) {
        System.out.println((ok ? "ok   " : "FAIL ") + name);
        if (ok) {
            passes++;
        } else {
            failures++;
        }
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        CareJobsClient client = new CareJobsClient();

        List<Map<String, Object>> tools = client.tools();
        List<Object> names = new ArrayList<>();
        for (Map<String, Object> tool : tools) {
            names.add(tool.get("name"));
        }
        check("tools " + names, names.contains("search_healthcare_jobs") && names.contains("get_healthcare_pay")
                && names.contains("get_healthcare_job_market") && names.contains("get_healthcare_jobs_by_state")
                && names.contains("list_healthcare_employers") && names.contains("get_healthcare_employer"));
        Map<String, Object> props = (Map<String, Object>) ((Map<String, Object>) tools.get(names.indexOf("search_healthcare_jobs")).get("inputSchema")).get("properties");
        List<Object> kindEnum = (List<Object>) ((Map<String, Object>) props.get("kind")).get("enum");
        check("Kind matches the live schema", kindEnum.equals(Kind.slugs()));

        Result jobs = client.searchJobs(new SearchParams().kind(Kind.NURSES).state("TX").limit(3));
        check("searchJobs: " + jobs.getLong("total") + " nursing jobs in TX", jobs.getLong("total") > 0
                && jobs.getMaps("jobs").size() <= 3 && !jobs.text().isEmpty());
        if (!jobs.getMaps("jobs").isEmpty()) {
            Map<String, Object> job = jobs.getMaps("jobs").get(0);
            check("job has title, employer and url", job.get("title") instanceof String && job.get("employer") instanceof String
                    && String.valueOf(job.get("url")).startsWith("https://carejobs.now/"));
        }

        Result pay = client.payByKind(Kind.NURSES);
        check("payByKind: median " + pay.getMap("pay").get("median"), pay.getMap("pay").get("median") instanceof Number);
        check("pay overview", !client.pay().getMaps("pay_by_kind").isEmpty());

        Result market = client.jobMarket();
        check("jobMarket: " + market.getLong("open_jobs") + " open", market.getLong("open_jobs") > 0);

        Result ohio = client.states("OH", Kind.NURSING_ASSISTANTS);
        check("states OH CNAs: " + ohio.getLong("open_jobs"), ohio.getLong("open_jobs") != null);
        check("every state", !client.states().getMaps("states").isEmpty());

        Result kaiser = client.employers(new EmployerParams().query("Kaiser").limit(3));
        check("employers Kaiser", !kaiser.getMaps("entries").isEmpty() && kaiser.getMaps("entries").size() <= 3);
        Result one = client.employer("kaiser-permanente");
        check("employer kaiser-permanente: " + one.getString("name"), one.getString("name") != null);

        try {
            client.employer("no-such-employer-xyz");
            check("unknown employer is a tool error", false);
        } catch (CareJobsException e) {
            check("unknown employer is a tool error", e.reason() == CareJobsException.Reason.TOOL);
        }

        System.out.println(failures == 0 ? "all " + passes + " passed" : failures + " failed, " + passes + " passed");
        System.exit(failures == 0 ? 0 : 1);
    }
}
