package com.example.minijarvis.ai;

/** The per-day AI call cap was reached. Mapped to HTTP 429. */
public class DailyLimitExceededException extends RuntimeException {
    public DailyLimitExceededException(int limit) {
        super("Daily AI call limit reached (" + limit + "). Raise MAX_AGENT_RUNS_PER_DAY or try again tomorrow.");
    }
}
