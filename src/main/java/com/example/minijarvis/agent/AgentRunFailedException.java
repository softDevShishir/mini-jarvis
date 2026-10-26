package com.example.minijarvis.agent;

/** An agent run was recorded as FAILED. The cause decides the HTTP status. */
public class AgentRunFailedException extends RuntimeException {

    private final long runId;

    public AgentRunFailedException(long runId, RuntimeException cause) {
        super(cause.getMessage(), cause);
        this.runId = runId;
    }

    public long getRunId() {
        return runId;
    }
}
