package now.carejobs;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The filters of {@link CareJobsClient#searchJobs(SearchParams)}. Every filter is
 * optional; one left unset (or set to null) is not sent.
 *
 * <pre>{@code
 * new SearchParams().query("ICU").kind(Kind.NURSES).state("TX").limit(5)
 * }</pre>
 */
public final class SearchParams {

    /** The default order: a mix of new and well-paid jobs. */
    public static final String SORT_MIXED = "mixed";
    /** Newest first. */
    public static final String SORT_NEWEST = "newest";
    /** Highest posted pay first. */
    public static final String SORT_HIGHEST_PAY = "highest_pay";

    private String query;
    private String kind;
    private String employer;
    private String state;
    private boolean remote;
    private Integer minHourlyUsd;
    private String sort;
    private Integer limit;

    /** Creates empty filters: every open job. */
    public SearchParams() {
    }

    /**
     * Words every job must carry in its title, place, department or schedule.
     *
     * @param query for example {@code "ICU"}, {@code "night"} or {@code "Houston"}
     * @return these filters
     */
    public SearchParams query(String query) {
        this.query = query;
        return this;
    }

    /**
     * A kind of job.
     *
     * @param kind the kind
     * @return these filters
     */
    public SearchParams kind(Kind kind) {
        this.kind = kind == null ? null : kind.value();
        return this;
    }

    /**
     * A kind of job by slug, for a kind this version does not know yet.
     *
     * @param kind a slug such as {@code "nurses"}
     * @return these filters
     */
    public SearchParams kind(String kind) {
        this.kind = kind;
        return this;
    }

    /**
     * An employer.
     *
     * @param employer a slug ({@code "kaiser-permanente"}) or name
     * @return these filters
     */
    public SearchParams employer(String employer) {
        this.employer = employer;
        return this;
    }

    /**
     * A US state.
     *
     * @param state {@code "TX"} or {@code "Texas"}
     * @return these filters
     */
    public SearchParams state(String state) {
        this.state = state;
        return this;
    }

    /**
     * Keep only remote jobs when true.
     *
     * @param remote true for remote jobs only
     * @return these filters
     */
    public SearchParams remote(boolean remote) {
        this.remote = remote;
        return this;
    }

    /**
     * Only jobs whose posted hourly ceiling reaches this many US dollars.
     *
     * @param minHourlyUsd 1 to 1000
     * @return these filters
     */
    public SearchParams minHourlyUsd(Integer minHourlyUsd) {
        this.minHourlyUsd = minHourlyUsd;
        return this;
    }

    /**
     * The order: {@link #SORT_MIXED} (default), {@link #SORT_NEWEST} or
     * {@link #SORT_HIGHEST_PAY}.
     *
     * @param sort the order
     * @return these filters
     */
    public SearchParams sort(String sort) {
        this.sort = sort;
        return this;
    }

    /**
     * How many jobs to return.
     *
     * @param limit 1 to 25 (default 10)
     * @return these filters
     */
    public SearchParams limit(Integer limit) {
        this.limit = limit;
        return this;
    }

    Map<String, Object> arguments() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("query", query);
        args.put("kind", kind);
        args.put("employer", employer);
        args.put("state", state);
        args.put("remote", remote ? Boolean.TRUE : null);
        args.put("min_hourly_usd", minHourlyUsd);
        args.put("sort", sort);
        args.put("limit", limit);
        return args;
    }
}
