package now.carejobs;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The filters of {@link CareJobsClient#employers(EmployerParams)}. Every filter is
 * optional; one left unset (or set to null) is not sent.
 */
public final class EmployerParams {

    /** Hospitals, clinics and care companies (the default without a query). */
    public static final String TYPE_EMPLOYER = "employer";
    /** Programs paying health professionals for remote AI work. */
    public static final String TYPE_AI_PROGRAM = "ai-program";
    /** Sources of other employers' jobs. */
    public static final String TYPE_JOB_BOARD = "job-board";

    private String query;
    private String type;
    private String care;
    private String state;
    private String kind;
    private Integer limit;

    /** Creates empty filters: the employers with the most open jobs. */
    public EmployerParams() {
    }

    /**
     * Part of the name.
     *
     * @param query for example {@code "Kaiser"} or {@code "children"}
     * @return these filters
     */
    public EmployerParams query(String query) {
        this.query = query;
        return this;
    }

    /**
     * {@link #TYPE_EMPLOYER}, {@link #TYPE_AI_PROGRAM} or {@link #TYPE_JOB_BOARD}.
     *
     * @param type the type
     * @return these filters
     */
    public EmployerParams type(String type) {
        this.type = type;
        return this;
    }

    /**
     * A kind of care, for example {@code "home-care"} or {@code "health-system"}.
     *
     * @param care the kind of care
     * @return these filters
     */
    public EmployerParams care(String care) {
        this.care = care;
        return this;
    }

    /**
     * Keep employers with open jobs in this US state.
     *
     * @param state {@code "TX"} or {@code "Texas"}
     * @return these filters
     */
    public EmployerParams state(String state) {
        this.state = state;
        return this;
    }

    /**
     * Keep employers with open jobs of this kind.
     *
     * @param kind the kind
     * @return these filters
     */
    public EmployerParams kind(Kind kind) {
        this.kind = kind == null ? null : kind.value();
        return this;
    }

    /**
     * Keep employers with open jobs of this kind, by slug.
     *
     * @param kind a slug such as {@code "pharmacy"}
     * @return these filters
     */
    public EmployerParams kind(String kind) {
        this.kind = kind;
        return this;
    }

    /**
     * How many employers to return.
     *
     * @param limit 1 to 100 (default 20)
     * @return these filters
     */
    public EmployerParams limit(Integer limit) {
        this.limit = limit;
        return this;
    }

    Map<String, Object> arguments() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("query", query);
        args.put("type", type);
        args.put("care", care);
        args.put("state", state);
        args.put("kind", kind);
        args.put("limit", limit);
        return args;
    }
}
