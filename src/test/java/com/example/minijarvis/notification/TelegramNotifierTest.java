package com.example.minijarvis.notification;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.example.minijarvis.TestFixtures;
import com.example.minijarvis.agent.AgentRun;
import com.example.minijarvis.agent.RiskLevel;
import com.example.minijarvis.audit.AuditEventType;
import com.example.minijarvis.audit.AuditService;
import com.example.minijarvis.config.JarvisProperties;
import com.example.minijarvis.recommendation.Recommendation;

class TelegramNotifierTest {

    private static final String TOKEN = "123456:SECRET-BOT-TOKEN";

    private final AuditService audit = mock(AuditService.class);
    private final AgentRun run = new AgentRun("cash-flow", "admin", Instant.now());
    private final List<Recommendation> recs = List.of(new Recommendation(1L, "cash-flow", "Chase Coastal", "r", "a",
            RiskLevel.HIGH, "claude", "m", false, false, Instant.now()));

    private static JarvisProperties props(String token, String chatId) {
        JarvisProperties base = TestFixtures.properties("claude", "");
        return new JarvisProperties(base.timezone(), base.maxAgentRunsPerDay(), base.ai(), base.security(),
                base.scheduler(), new JarvisProperties.Telegram(token, chatId, "https://telegram.test"));
    }

    @Test
    void skipsSilentlyWhenNotConfigured() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var notifier = new TelegramNotifier(props("", ""), builder, audit);

        notifier.notifyNewRecommendations(run, recs);

        server.verify(); // no requests expected
        verifyNoInteractions(audit);
    }

    @Test
    void sendsMessageAndAudits() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://telegram.test/bot" + TOKEN + "/sendMessage"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.chat_id").value("42"))
                .andRespond(withSuccess("{\"ok\":true}", org.springframework.http.MediaType.APPLICATION_JSON));
        var notifier = new TelegramNotifier(props(TOKEN, "42"), builder, audit);

        notifier.notifyNewRecommendations(run, recs);

        server.verify();
        verify(audit).record(eq(AuditEventType.NOTIFICATION_SENT), eq("telegram"), any(), any(), any());
    }

    @Test
    void failureNeverThrowsAndDoesNotLeakToken() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://telegram.test/bot" + TOKEN + "/sendMessage")).andRespond(withServerError());
        var notifier = new TelegramNotifier(props(TOKEN, "42"), builder, audit);

        assertThatCode(() -> notifier.notifyNewRecommendations(run, recs)).doesNotThrowAnyException();
        verify(audit).record(eq(AuditEventType.NOTIFICATION_FAILED), eq("telegram"), any(), any(),
                argThat(details -> !details.contains("SECRET-BOT-TOKEN")));
    }
}
