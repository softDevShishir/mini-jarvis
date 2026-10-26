package com.example.minijarvis.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.minijarvis.TestFixtures;
import com.example.minijarvis.TestFixtures.FakeProvider;
import com.example.minijarvis.ai.AiProviderException.Kind;
import com.example.minijarvis.audit.AuditEventType;
import com.example.minijarvis.audit.AuditService;

class AiRouterTest {

    private final DailyAiCallLimiter limiter = mock(DailyAiCallLimiter.class);
    private final AuditService audit = mock(AuditService.class);

    private AiRouter router(String defaultId, String fallbackId, AiProvider... providers) {
        return new AiRouter(List.of(providers), TestFixtures.properties(defaultId, fallbackId), limiter, audit);
    }

    @Test
    void usesDefaultProviderWhenNoOverride() {
        FakeProvider claude = FakeProvider.answering("claude", "from claude");
        FakeProvider gemini = FakeProvider.answering("gemini", "from gemini");

        RoutedAiResult result = router("claude", "", claude, gemini).generate(null, "sys", "user");

        assertThat(result.providerId()).isEqualTo("claude");
        assertThat(result.result().text()).isEqualTo("from claude");
        assertThat(result.fallbackUsed()).isFalse();
        assertThat(gemini.calls).isZero();
    }

    @Test
    void requestOverrideWinsOverDefault() {
        FakeProvider claude = FakeProvider.answering("claude", "from claude");
        FakeProvider gemini = FakeProvider.answering("gemini", "from gemini");

        RoutedAiResult result = router("claude", "", claude, gemini).generate("GEMINI", "sys", "user");

        assertThat(result.providerId()).isEqualTo("gemini");
        assertThat(claude.calls).isZero();
    }

    @Test
    void unavailableProviderIsRejected() {
        FakeProvider claude = FakeProvider.answering("claude", "x");
        FakeProvider gemini = new FakeProvider("gemini", false, n -> "x");

        assertThatThrownBy(() -> router("claude", "", claude, gemini).generate("gemini", "s", "u"))
                .isInstanceOf(ProviderUnavailableException.class)
                .hasMessageContaining("not available");
        assertThat(gemini.calls).isZero();
    }

    @Test
    void unavailableDefaultProviderIsRejected() {
        FakeProvider claude = new FakeProvider("claude", false, n -> "x");

        assertThatThrownBy(() -> router("claude", "", claude).generate(null, "s", "u"))
                .isInstanceOf(ProviderUnavailableException.class);
    }

    @Test
    void unknownProviderIsRejected() {
        assertThatThrownBy(() -> router("claude", "", FakeProvider.answering("claude", "x")).generate("gpt", "s", "u"))
                .isInstanceOf(ProviderUnavailableException.class)
                .hasMessageContaining("Unknown AI provider 'gpt'");
    }

    @Test
    void rateLimitIsRetriedOnceThenSucceeds() {
        FakeProvider claude = new FakeProvider("claude", true, n -> {
            if (n == 1) {
                throw new AiProviderException("claude", Kind.RATE_LIMITED, 429, null);
            }
            return "ok";
        });

        RoutedAiResult result = router("claude", "", claude).generate(null, "s", "u");

        assertThat(result.result().text()).isEqualTo("ok");
        assertThat(result.attempts()).isEqualTo(2);
        assertThat(result.fallbackUsed()).isFalse();
        assertThat(claude.calls).isEqualTo(2);
    }

    @Test
    void rateLimitTwiceFallsBackAndRecordsIt() {
        FakeProvider claude = FakeProvider.failing("claude", Kind.RATE_LIMITED, 429);
        FakeProvider gemini = FakeProvider.answering("gemini", "from gemini");

        RoutedAiResult result = router("claude", "gemini", claude, gemini).generate(null, "s", "u");

        assertThat(claude.calls).isEqualTo(2);
        assertThat(gemini.calls).isEqualTo(1);
        assertThat(result.providerId()).isEqualTo("gemini");
        assertThat(result.fallbackUsed()).isTrue();
        assertThat(result.attempts()).isEqualTo(3);
        verify(audit).record(eq(AuditEventType.AI_FALLBACK_USED), anyString(), isNull(), isNull(), anyString());
    }

    @Test
    void anyRegisteredProviderCanBeTheFallback() {
        FakeProvider gemini = FakeProvider.failing("gemini", Kind.TEMPORARY, 503);
        FakeProvider openai = FakeProvider.answering("openai", "from openai");

        RoutedAiResult result = router("gemini", "openai", gemini, openai).generate(null, "s", "u");

        assertThat(result.providerId()).isEqualTo("openai");
        assertThat(result.fallbackUsed()).isTrue();
    }

    @Test
    void temporaryErrorTwiceWithoutFallbackThrows() {
        FakeProvider claude = FakeProvider.failing("claude", Kind.TEMPORARY, 503);

        assertThatThrownBy(() -> router("claude", "", claude).generate(null, "s", "u"))
                .isInstanceOf(AiProviderException.class)
                .satisfies(e -> assertThat(((AiProviderException) e).getKind()).isEqualTo(Kind.TEMPORARY));
        assertThat(claude.calls).isEqualTo(2);
    }

    @Test
    void fallbackThatIsUnavailableIsSkipped() {
        FakeProvider claude = FakeProvider.failing("claude", Kind.RATE_LIMITED, 429);
        FakeProvider gemini = new FakeProvider("gemini", false, n -> "x");

        assertThatThrownBy(() -> router("claude", "gemini", claude, gemini).generate(null, "s", "u"))
                .isInstanceOf(AiProviderException.class);
        assertThat(gemini.calls).isZero();
    }

    @Test
    void invalidKeyIsNotRetriedAndDoesNotFallBack() {
        FakeProvider claude = FakeProvider.failing("claude", Kind.AUTHENTICATION, 401);
        FakeProvider gemini = FakeProvider.answering("gemini", "x");

        assertThatThrownBy(() -> router("claude", "gemini", claude, gemini).generate(null, "s", "u"))
                .isInstanceOf(AiProviderException.class)
                .satisfies(e -> assertThat(((AiProviderException) e).getKind()).isEqualTo(Kind.AUTHENTICATION));
        assertThat(claude.calls).isEqualTo(1);
        assertThat(gemini.calls).isZero();
        verify(audit, never()).record(eq(AuditEventType.AI_FALLBACK_USED), any(), any(), any(), any());
    }

    @Test
    void badRequestIsNotRetried() {
        FakeProvider claude = FakeProvider.failing("claude", Kind.BAD_REQUEST, 400);

        assertThatThrownBy(() -> router("claude", "", claude).generate(null, "s", "u"))
                .isInstanceOf(AiProviderException.class);
        assertThat(claude.calls).isEqualTo(1);
    }

    @Test
    void everyAttemptCountsAgainstTheDailyCap() {
        FakeProvider claude = FakeProvider.failing("claude", Kind.RATE_LIMITED, 429);
        FakeProvider gemini = FakeProvider.answering("gemini", "x");

        router("claude", "gemini", claude, gemini).generate(null, "s", "u");

        verify(limiter, org.mockito.Mockito.times(2)).acquire(eq("claude"), any());
        verify(limiter).acquire(eq("gemini"), any());
    }

    @Test
    void dailyCapStopsTheCall() {
        FakeProvider claude = FakeProvider.answering("claude", "x");
        org.mockito.Mockito.doThrow(new DailyLimitExceededException(20)).when(limiter).acquire(any(), any());

        assertThatThrownBy(() -> router("claude", "", claude).generate(null, "s", "u"))
                .isInstanceOf(DailyLimitExceededException.class);
        assertThat(claude.calls).isZero();
    }
}
