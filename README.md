# carejobs-java

Java client for the [CareJobs.Now](https://carejobs.now) healthcare jobs API. Search open US healthcare jobs, see the hourly pay employers post, and look up employers and states.

- Free, no API key.
- Java 11 or newer. No dependencies: it uses the JDK's `java.net.http.HttpClient` and reads JSON itself.
- A named module, `now.carejobs`, that also works on the class path.
- Typed filters, a `Kind` enum, one unchecked `CareJobsException` type. A client is safe to share between threads.
- The same API is a remote MCP server at <https://carejobs.now/api/mcp>, so any MCP client can use it too.

## Where the data comes from

CareJobs.Now is a free US healthcare job board. Its jobs are read daily from the career sites of US hospitals, health systems, clinics and care employers, and the pay is what each posting states (hourly rates before shift differentials and bonuses). Every job links to its page on CareJobs.Now, where the user applies. Data and docs: <https://carejobs.now/data>.

Data from CareJobs.Now (https://carejobs.now), CC BY 4.0.

## Install

Maven:

```xml
<dependency>
  <groupId>now.carejobs</groupId>
  <artifactId>carejobs</artifactId>
  <version>1.0.0</version>
</dependency>
```

Gradle:

```kotlin
implementation("now.carejobs:carejobs:1.0.0")
```

With modules, add `requires now.carejobs;` to your `module-info.java`.

## Quick start

```java
import now.carejobs.CareJobsClient;
import now.carejobs.Kind;
import now.carejobs.Result;
import now.carejobs.SearchParams;
import java.util.Map;

CareJobsClient client = new CareJobsClient();

Result jobs = client.searchJobs(new SearchParams().query("ICU").kind(Kind.NURSES).state("TX").limit(5));
System.out.println(jobs.getLong("total") + " jobs match");
for (Map<String, Object> job : jobs.getMaps("jobs")) {
    System.out.println(job.get("title") + " | " + job.get("employer") + " | " + job.get("location") + " | " + job.get("url"));
}

Result pay = client.payByKind(Kind.NURSES);
System.out.println("Median posted pay for nurses: $" + pay.getMap("pay").get("median") + "/hr");

// Every answer also carries the server's readable summary:
System.out.println(pay.text());
```

A runnable version is in [`examples/Search.java`](examples/Search.java): `java -cp carejobs-1.0.0.jar examples/Search.java OH`.

## Methods

Every method returns a `Result`: the tool's data as a read-only map (`result.data()`, or `result.get("jobs")`) plus `result.text()`, a readable summary written by the server. The keys are the ones the API sends (`typical_low`, `open_jobs`, and so on). Objects are `Map<String, Object>`, arrays `List<Object>`, whole numbers `Long` and other numbers `Double`. Where the API sends `null`, the value is `null`. `getString`, `getLong`, `getDouble`, `getBoolean`, `getMap`, `getList` and `getMaps` save the casts, and `toJson()` gives the data back as JSON.

Every filter is optional: one left unset is not sent. A kind may be a `Kind` constant or its slug (`"nurses"`).

| Method | What it answers |
| --- | --- |
| `searchJobs(SearchParams)`, `searchJobs(query)` | Open jobs. Filters: `query`, `kind`, `employer`, `state`, `remote`, `minHourlyUsd`, `sort`, `limit`. Data: `total`, `pay`, `jobs`, `more`. `limit` is 1 to 25. `sort` is `mixed`, `newest` or `highest_pay` (`SearchParams.SORT_*`). |
| `pay()`, `pay(kind, employer)` | Posted hourly pay: every kind, one kind with the employers posting the most, one employer, or both. |
| `payByKind(kind)` | `pay` for one kind of job. |
| `payByEmployer(employer)` | `pay` for one employer. |
| `jobMarket()` | How many jobs are open, new in the last 7 days, and the open jobs by kind, state and employer. |
| `jobsByState()`, `jobsByState(state)`, `jobsByState(state, kind)`, `jobsByState(StateParams)` | Every state, one state, or one kind of job in one state (a kind needs a state). |
| `states(...)` | Alias of `jobsByState`. |
| `employers()`, `employers(query)`, `employers(EmployerParams)` | The employers, AI-training programs and job boards the site reads. Filters: `query`, `type`, `care`, `state`, `kind`, `limit` (1 to 100). |
| `employer(slug)` | One employer by slug (`"kaiser-permanente"`) or name. |
| `kinds()` | The 17 kinds of job as slugs. Also `Kind.values()` and `Kind.slugs()`. |
| `tools()` | The server's tools with their input schemas. |
| `callTool(name, arguments)` | Any tool, for a tool this version does not know yet. |

```java
Result ohio = client.states("OH", Kind.NURSING_ASSISTANTS);
System.out.println(ohio.getLong("open_jobs") + " CNA jobs in Ohio");

Result kaiser = client.employers(new EmployerParams().query("Kaiser").limit(3));
for (Map<String, Object> entry : kaiser.getMaps("entries")) {
    System.out.println(entry.get("name") + " (" + entry.get("open_jobs") + " open jobs)");
}
```

### Options

```java
CareJobsClient client = CareJobsClient.builder()
        .baseUrl("https://carejobs.now")      // default
        .timeout(Duration.ofSeconds(60))      // default 60 seconds
        .userAgent("my-app/1.0")              // default "carejobs-java/<version>"
        .build();
```

The server answers a query it has not seen before in up to about 25 seconds, then repeats of it in a fraction of one, which is why the default timeout is a minute. `transport(...)` replaces the HTTP call, for tests or another HTTP stack.

### Errors

Every failure throws `CareJobsException`, which is unchecked. Its `reason()` says which kind:

```java
try {
    client.jobsByState(new StateParams().kind(Kind.NURSES)); // a kind without a state
} catch (CareJobsException e) {
    System.out.println(e.reason() + ": " + e.getMessage());
    // TOOL: the server ran the tool and said no
    // RPC: a JSON-RPC error (e.rpcCode())
    // HTTP: a non-2xx status (e.status())
    // NETWORK, TIMEOUT: no answer
    // CANCELED: the thread was interrupted while it waited
    // PROTOCOL: the answer was not what MCP promises
}
```

### Kinds

`nurses`, `nurse-practitioners`, `physicians`, `nursing-assistants`, `caregivers`, `therapy-rehab`, `mental-health`, `pharmacy`, `imaging`, `lab`, `surgical-techs`, `emts-paramedics`, `medical-assistants`, `dental`, `admin`, `allied-health`, `ai-training`.

## Build and tests

No Maven needed: the JDK (17 or newer to build, the output runs on 11) is enough.

```sh
scripts/build.sh          # compiles for Java 11, runs the offline tests, makes the jars in build/
scripts/build.sh --live   # the same, plus the tests against the live API
```

The offline tests use a fake transport and answers recorded from the live API (in `src/test/resources/fixtures`), so nothing leaves the machine. `pom.xml` also builds the library with Maven.

## Plain JSON

Without this library the same data is plain JSON: <https://carejobs.now/roles.json> (open jobs by kind of job and employer) and <https://carejobs.now/pay.json> (posted hourly pay by kind of job).

## License

Code: MIT, copyright O-mega Enterprise, Inc. Data: CC BY 4.0, credit CareJobs.Now.
