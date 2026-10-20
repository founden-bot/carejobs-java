/**
 * Client for the free CareJobs.Now US healthcare jobs API: search open jobs, see
 * the hourly pay employers post, and look up employers and states. No API key.
 * Start with {@link now.carejobs.CareJobsClient}.
 */
module now.carejobs {
    requires java.net.http;

    exports now.carejobs;
}
