package com.example.minijarvis.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.example.minijarvis.ai.AiProviderException.Kind;
import com.google.genai.errors.ApiException;

class AiProviderExceptionTest {

    @Test
    void classifiesHttpStatuses() {
        assertThat(AiProviderException.kindForStatus(429)).isEqualTo(Kind.RATE_LIMITED);
        assertThat(AiProviderException.kindForStatus(401)).isEqualTo(Kind.AUTHENTICATION);
        assertThat(AiProviderException.kindForStatus(403)).isEqualTo(Kind.AUTHENTICATION);
        assertThat(AiProviderException.kindForStatus(400)).isEqualTo(Kind.BAD_REQUEST);
        assertThat(AiProviderException.kindForStatus(404)).isEqualTo(Kind.BAD_REQUEST);
        assertThat(AiProviderException.kindForStatus(500)).isEqualTo(Kind.TEMPORARY);
        assertThat(AiProviderException.kindForStatus(529)).isEqualTo(Kind.TEMPORARY);
    }

    @Test
    void onlyRateLimitAndTemporaryAreRetryable() {
        assertThat(new AiProviderException("x", Kind.RATE_LIMITED, 429, null).isRetryable()).isTrue();
        assertThat(new AiProviderException("x", Kind.TEMPORARY, 503, null).isRetryable()).isTrue();
        assertThat(new AiProviderException("x", Kind.AUTHENTICATION, 401, null).isRetryable()).isFalse();
        assertThat(new AiProviderException("x", Kind.BAD_REQUEST, 400, null).isRetryable()).isFalse();
    }

    @Test
    void geminiInvalidKeyOn400IsTreatedAsAuthentication() {
        var invalidKey = new ApiException(400, "INVALID_ARGUMENT", "API key not valid. Please pass a valid API key.");
        var badRequest = new ApiException(400, "INVALID_ARGUMENT", "Invalid JSON payload");
        var quota = new ApiException(429, "RESOURCE_EXHAUSTED", "Quota exceeded");

        assertThat(GeminiProvider.classify(invalidKey)).isEqualTo(Kind.AUTHENTICATION);
        assertThat(GeminiProvider.classify(badRequest)).isEqualTo(Kind.BAD_REQUEST);
        assertThat(GeminiProvider.classify(quota)).isEqualTo(Kind.RATE_LIMITED);
    }

    @Test
    void messageDoesNotIncludeSdkDetailsAndExplainsGeminiQuota() {
        var cause = new RuntimeException("raw sdk text with request details");
        var e = new AiProviderException("gemini", Kind.RATE_LIMITED, 429, cause);

        assertThat(e.getMessage()).doesNotContain("raw sdk text").contains("quota limit, not a bug");
    }
}
