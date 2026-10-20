package now.carejobs;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;

/**
 * Sends one POST request. The client's default uses the JDK's
 * {@link java.net.http.HttpClient}; pass your own to
 * {@link CareJobsClient.Builder#transport(Transport)} for tests or for another
 * HTTP stack.
 *
 * <p>Throw {@link java.net.http.HttpTimeoutException} for a timeout, any other
 * {@link IOException} for a network failure, and let
 * {@link InterruptedException} through. The client turns each into a
 * {@link CareJobsException}.
 */
@FunctionalInterface
public interface Transport {

    /**
     * Sends the request and returns the answer, whatever its status.
     *
     * @param uri     the MCP endpoint
     * @param body    the JSON-RPC request
     * @param headers the request headers
     * @param timeout how long the request may take
     * @return the answer
     * @throws IOException          when the request fails
     * @throws InterruptedException when the thread is interrupted while it waits
     */
    HttpReply post(URI uri, String body, Map<String, String> headers, Duration timeout) throws IOException, InterruptedException;
}
