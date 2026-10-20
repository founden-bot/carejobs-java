// java -cp carejobs-1.0.0.jar examples/Search.java OH
import now.carejobs.CareJobsClient;
import now.carejobs.Kind;
import now.carejobs.Result;
import now.carejobs.SearchParams;
import java.util.Map;

public class Search {
    public static void main(String[] args) {
        String state = args.length > 0 ? args[0] : "TX";
        CareJobsClient client = new CareJobsClient();

        Result res = client.searchJobs(new SearchParams().kind(Kind.NURSES).state(state).limit(5));
        System.out.println(res.getLong("total") + " nursing jobs in " + state);
        for (Map<String, Object> job : res.getMaps("jobs")) {
            Object location = job.get("location") == null ? "" : job.get("location");
            System.out.println(job.get("title") + " | " + job.get("employer") + " | " + location + " | " + job.get("url"));
        }
    }
}
