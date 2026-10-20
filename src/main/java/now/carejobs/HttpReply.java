package now.carejobs;

import java.util.Objects;

/** One HTTP answer as a {@link Transport} returns it: status, content type and body. */
public final class HttpReply {

    private final int status;
    private final String contentType;
    private final String body;

    /**
     * Creates an answer.
     *
     * @param status      the HTTP status, for example 200
     * @param contentType the Content-Type header, or an empty string
     * @param body        the body as text
     */
    public HttpReply(int status, String contentType, String body) {
        this.status = status;
        this.contentType = contentType == null ? "" : contentType;
        this.body = Objects.requireNonNull(body, "body");
    }

    /**
     * The HTTP status.
     *
     * @return the status
     */
    public int status() {
        return status;
    }

    /**
     * The Content-Type header.
     *
     * @return the header value, empty when there was none
     */
    public String contentType() {
        return contentType;
    }

    /**
     * The body.
     *
     * @return the body as text
     */
    public String body() {
        return body;
    }
}
