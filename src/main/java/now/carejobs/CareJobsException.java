package now.carejobs;

/**
 * Every failure of the client. {@link #reason()} says what kind of failure it is.
 *
 * <p>It is unchecked, so a call needs no {@code throws} clause.
 */
public final class CareJobsException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** What kind of failure a {@link CareJobsException} is. */
    public enum Reason {
        /** The server ran the tool and said no; the message is its text. */
        TOOL("tool"),
        /** A JSON-RPC error; see {@link CareJobsException#rpcCode()}. */
        RPC("rpc"),
        /** A status outside 2xx; see {@link CareJobsException#status()}. */
        HTTP("http"),
        /** The server could not be reached. */
        NETWORK("network"),
        /** No answer in time. */
        TIMEOUT("timeout"),
        /** The calling thread was interrupted while it waited. */
        CANCELED("canceled"),
        /** The answer was not what MCP promises. */
        PROTOCOL("protocol");

        private final String value;

        Reason(String value) {
            this.value = value;
        }

        /**
         * The reason as a lower-case word, for example {@code "tool"}.
         *
         * @return the word
         */
        public String value() {
            return value;
        }

        @Override
        public String toString() {
            return value;
        }
    }

    /** What kind of failure this is. */
    private final Reason reason;
    /** The tool that was called, or null. */
    private final String tool;
    /** The HTTP status of an HTTP failure, or null. */
    private final Integer status;
    /** The JSON-RPC error code of an RPC failure, or null. */
    private final Integer rpcCode;

    /**
     * Creates the exception.
     *
     * @param message what went wrong
     * @param reason  what kind of failure it is
     * @param tool    the tool that was called, or null
     * @param status  the HTTP status for {@link Reason#HTTP}, else null
     * @param rpcCode the JSON-RPC error code for {@link Reason#RPC}, else null
     * @param cause   the underlying exception, or null
     */
    public CareJobsException(String message, Reason reason, String tool, Integer status, Integer rpcCode, Throwable cause) {
        super(message, cause);
        this.reason = reason;
        this.tool = tool;
        this.status = status;
        this.rpcCode = rpcCode;
    }

    CareJobsException(String message, Reason reason, String tool) {
        this(message, reason, tool, null, null, null);
    }

    CareJobsException(String message, Reason reason, String tool, Throwable cause) {
        this(message, reason, tool, null, null, cause);
    }

    /**
     * What kind of failure this is.
     *
     * @return the reason, never null
     */
    public Reason reason() {
        return reason;
    }

    /**
     * The tool that was called, for example {@code "search_healthcare_jobs"}.
     *
     * @return the tool name, or null for a request that was not a tool call
     */
    public String tool() {
        return tool;
    }

    /**
     * The HTTP status of a {@link Reason#HTTP} failure.
     *
     * @return the status, or null for other reasons
     */
    public Integer status() {
        return status;
    }

    /**
     * The JSON-RPC error code of a {@link Reason#RPC} failure.
     *
     * @return the code, or null for other reasons
     */
    public Integer rpcCode() {
        return rpcCode;
    }
}
