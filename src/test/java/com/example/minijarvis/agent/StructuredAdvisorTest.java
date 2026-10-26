package com.example.minijarvis.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.minijarvis.TestFixtures;
import com.example.minijarvis.ai.AiProvider;
import com.example.minijarvis.ai.AiResult;
import com.example.minijarvis.ai.AiRouter;
import com.example.minijarvis.ai.DailyAiCallLimiter;
import com.example.minijarvis.audit.AuditService;
import com.fasterxml.jackson.databind.ObjectMapper;

/** JSON parsing and the invalid-JSON retry path, driven through a mocked AiProvider. */
class StructuredAdvisorTest {

    private AiProvider provider;
    private StructuredAdvisor advisor;

    @BeforeEach
    void setUp() {
        provider = mock(AiProvider.class);
        when(provider.id()).thenReturn("claude");
        when(provider.isAvailable()).thenReturn(true);
        AiRouter router = new AiRouter(List.of(provider), TestFixtures.properties("claude", ""),
                mock(DailyAiCallLimiter.class), mock(AuditService.class));
        advisor = new StructuredAdvisor(router, new AdviceParser(new ObjectMapper()));
    }

    private static AiResult text(String t) {
        return new AiResult(t, "m", 100, 50);
    }

    @Test
    void validJsonNeedsOneCall() {
        when(provider.generate(anyString(), anyString())).thenReturn(text(TestFixtures.VALID_ADVICE));

        StructuredAdvisor.Response response = advisor.advise(null, "sys", "user");

        assertThat(response.payload().recommendations()).hasSize(2);
        assertThat(response.inputTokens()).isEqualTo(100);
        verify(provider, times(1)).generate(anyString(), anyString());
    }

    @Test
    void codeFencedJsonIsAcceptedWithoutRetry() {
        when(provider.generate(anyString(), anyString()))
                .thenReturn(text("```json\n" + TestFixtures.VALID_ADVICE + "\n```"));

        assertThat(advisor.advise(null, "sys", "user").payload().riskLevel()).isEqualTo(RiskLevel.HIGH);
        verify(provider, times(1)).generate(anyString(), anyString());
    }

    @Test
    void invalidJsonIsRetriedOnceWithAStricterPrompt() {
        when(provider.generate(anyString(), anyString()))
                .thenReturn(text("Here you go: not json"))
                .thenReturn(text(TestFixtures.VALID_ADVICE));

        StructuredAdvisor.Response response = advisor.advise(null, "sys", "user");

        assertThat(response.payload().recommendations()).hasSize(2);
        assertThat(response.inputTokens()).isEqualTo(200);
        assertThat(response.outputTokens()).isEqualTo(100);
        verify(provider).generate(eq("sys"), eq("user"));
        verify(provider).generate(eq("sys"), contains("Return only valid JSON"));
    }

    @Test
    void invalidJsonTwiceFails() {
        when(provider.generate(anyString(), anyString())).thenReturn(text("{ broken"));

        assertThatThrownBy(() -> advisor.advise(null, "sys", "user"))
                .isInstanceOf(InvalidAdviceException.class)
                .hasMessageContaining("invalid JSON twice");
        verify(provider, times(2)).generate(anyString(), anyString());
    }
}
