package now.carejobs;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The filters of {@link CareJobsClient#jobsByState(StateParams)}. With no state:
 * every state. With a state: its jobs by kind, pay, towns, employers and newest
 * jobs. With a state and a kind: the same for that kind (a kind needs a state).
 */
public final class StateParams {

    private String state;
    private String kind;
    private Integer limit;

    /** Creates empty filters: every state. */
    public StateParams() {
    }

    /**
     * A US state.
     *
     * @param state {@code "OH"} or {@code "Ohio"}
     * @return these filters
     */
    public StateParams state(String state) {
        this.state = state;
        return this;
    }

    /**
     * A kind of job (needs a state).
     *
     * @param kind the kind
     * @return these filters
     */
    public StateParams kind(Kind kind) {
        this.kind = kind == null ? null : kind.value();
        return this;
    }

    /**
     * A kind of job by slug (needs a state).
     *
     * @param kind a slug such as {@code "nurses"}
     * @return these filters
     */
    public StateParams kind(String kind) {
        this.kind = kind;
        return this;
    }

    /**
     * How many of the newest jobs to return.
     *
     * @param limit 1 to 25
     * @return these filters
     */
    public StateParams limit(Integer limit) {
        this.limit = limit;
        return this;
    }

    Map<String, Object> arguments() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("state", state);
        args.put("kind", kind);
        args.put("limit", limit);
        return args;
    }
}
