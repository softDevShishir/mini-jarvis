package com.example.minijarvis.ai;

/** The single exception type callers see for any AI provider failure. */
public class AiProviderException extends RuntimeException {

    public enum Kind {
        /** HTTP 429 / quota exhausted. Retryable. */
        RATE_LIMITED,
        /** 5xx, overloaded, network/timeout. Retryable. */
        TEMPORARY,
        /** Invalid or missing API key, permission denied. Never retried. */
        AUTHENTICATION,
        /** Malformed request, unknown model. Never retried. */
        BAD_REQUEST,
        UNKNOWN
    }

    private final String providerId;
    private final Kind kind;
    private final Integer httpStatus;

    public AiProviderException(String providerId, Kind kind, Integer httpStatus, Throwable cause) {
        super(buildMessage(providerId, kind, httpStatus), cause);
        this.providerId = providerId;
        this.kind = kind;
        this.httpStatus = httpStatus;
    }

    public static Kind kindForStatus(int status) {
        if (status == 429) {
            return Kind.RATE_LIMITED;
        }
        if (status == 401 || status == 403) {
            return Kind.AUTHENTICATION;
        }
        if (status == 408 || status == 409 || status >= 500) {
            return Kind.TEMPORARY;
        }
        if (status >= 400) {
            return Kind.BAD_REQUEST;
        }
        return Kind.UNKNOWN;
    }

    // Deliberately does not include the SDK message: it stays in the cause for server-side logs only.
    private static String buildMessage(String providerId, Kind kind, Integer httpStatus) {
        String base = "AI provider '" + providerId + "' failed: " + kind + (httpStatus != null ? " (HTTP " + httpStatus + ")" : "");
        if (kind == Kind.RATE_LIMITED && "gemini".equals(providerId)) {
            base += ". Gemini free-tier quotas are small; this is a quota limit, not a bug";
        }
        if (kind == Kind.RATE_LIMITED && "openai".equals(providerId)) {
            base += ". OpenAI also returns 429 when the account has no credit (insufficient_quota); check billing";
        }
        return base;
    }

    public boolean isRetryable() {
        return kind == Kind.RATE_LIMITED || kind == Kind.TEMPORARY;
    }

    public String getProviderId() { return providerId; }
    public Kind getKind() { return kind; }
    public Integer getHttpStatus() { return httpStatus; }
}
