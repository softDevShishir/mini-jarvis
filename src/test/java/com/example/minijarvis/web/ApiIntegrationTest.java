package com.example.minijarvis.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.minijarvis.TestFixtures;
import com.example.minijarvis.agent.AgentRun;
import com.example.minijarvis.agent.AgentRunRepository;
import com.example.minijarvis.agent.AgentRunStatus;
import com.example.minijarvis.agent.RiskLevel;
import com.example.minijarvis.ai.AiProviderException;
import com.example.minijarvis.ai.AiResult;
import com.example.minijarvis.ai.ClaudeProvider;
import com.example.minijarvis.ai.GeminiProvider;
import com.example.minijarvis.audit.AuditEventType;
import com.example.minijarvis.audit.AuditLogEntry;
import com.example.minijarvis.audit.AuditLogRepository;
import com.example.minijarvis.notification.RecommendationNotifier;
import com.example.minijarvis.recommendation.Recommendation;
import com.example.minijarvis.recommendation.RecommendationRepository;
import com.example.minijarvis.recommendation.RecommendationStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Full app against in-memory H2 (real Flyway schema + seed data), with AI providers and Telegram mocked. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired RecommendationRepository recommendations;
    @Autowired AgentRunRepository runs;
    @Autowired AuditLogRepository auditLog;

    @MockitoBean ClaudeProvider claude;
    @MockitoBean GeminiProvider gemini;
    @MockitoBean RecommendationNotifier notifier;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        when(claude.id()).thenReturn("claude");
        when(claude.model()).thenReturn("test-claude-model");
        when(claude.isAvailable()).thenReturn(true);
        when(gemini.id()).thenReturn("gemini");
        when(gemini.model()).thenReturn("test-gemini-model");
        when(gemini.isAvailable()).thenReturn(false);
        token = login("admin", "test-password");
    }

    private String login(String user, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    private String bearer() {
        return "Bearer " + token;
    }

    private Recommendation pendingRecommendation() {
        AgentRun run = runs.save(new AgentRun("cash-flow", "test", Instant.now()));
        return recommendations.save(new Recommendation(run.getId(), "cash-flow", "Chase overdue invoices", "reason",
                "Call the customer", RiskLevel.HIGH, "claude", "test-claude-model", false, false, Instant.now()));
    }

    // ---------- auth ----------

    @Test
    void apiRequiresToken() throws Exception {
        mvc.perform(get("/api/recommendations"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Missing or invalid bearer token"));
        mvc.perform(post("/api/agents/cash-flow/run")).andExpect(status().isUnauthorized());
    }

    @Test
    void invalidTokenIsRejected() throws Exception {
        mvc.perform(get("/api/providers").header("Authorization", "Bearer not.a.valid.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginRejectsWrongPasswordAndOtherUsernames() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.accessToken").doesNotExist());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"mallory\",\"password\":\"test-password\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ---------- providers ----------

    @Test
    void providersEndpointReportsAvailability() throws Exception {
        mvc.perform(get("/api/providers").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultProvider").value("claude"))
                .andExpect(jsonPath("$.providers[?(@.id=='claude')].available").value(true))
                .andExpect(jsonPath("$.providers[?(@.id=='gemini')].available").value(false));
    }

    // ---------- agents ----------

    @Test
    void unknownAgentIs404() throws Exception {
        mvc.perform(post("/api/agents/does-not-exist/run").header("Authorization", bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Unknown agent 'does-not-exist'"));
    }

    @Test
    void unavailableProviderIs400AndNoRunIsCreated() throws Exception {
        long runsBefore = runs.count();
        mvc.perform(post("/api/agents/cash-flow/run").param("provider", "gemini").header("Authorization", bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("not available")));
        mvc.perform(post("/api/agents/cash-flow/run").param("provider", "openai").header("Authorization", bearer()))
                .andExpect(status().isBadRequest());
        assertThat(runs.count()).isEqualTo(runsBefore);
        verify(claude, never()).generate(anyString(), anyString());
    }

    @Test
    void runningCashFlowAgentCreatesPendingRecommendationsAndAuditTrail() throws Exception {
        when(claude.generate(anyString(), anyString()))
                .thenReturn(new AiResult(TestFixtures.VALID_ADVICE, "test-claude-model", 1200, 300));

        String body = mvc.perform(post("/api/agents/cash-flow/run").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.provider").value("claude"))
                .andExpect(jsonPath("$.model").value("test-claude-model"))
                .andExpect(jsonPath("$.riskLevel").value("HIGH"))
                .andExpect(jsonPath("$.needsReview").value(false))
                .andExpect(jsonPath("$.recommendations.length()").value(2))
                .andExpect(jsonPath("$.recommendations[0].status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        long runId = objectMapper.readTree(body).get("runId").asLong();

        // The prompt contained the real seed data and the required system prompt.
        verify(claude).generate(
                org.mockito.ArgumentMatchers.contains("Use ONLY the data provided. Never invent numbers. Return only valid JSON."),
                org.mockito.ArgumentMatchers.contains("Coastal Builders Pty Ltd"));

        List<Recommendation> saved = recommendations.findByAgentRunIdOrderByIdAsc(runId);
        assertThat(saved).hasSize(2).allMatch(r -> r.getStatus() == RecommendationStatus.PENDING
                && r.getProvider().equals("claude") && !r.isFallbackUsed());

        List<AuditEventType> events = auditLog.findAll().stream()
                .filter(e -> Long.valueOf(runId).equals(e.getAgentRunId()))
                .map(AuditLogEntry::getEventType).toList();
        assertThat(events).containsExactly(AuditEventType.AGENT_RUN_STARTED,
                AuditEventType.RECOMMENDATION_CREATED, AuditEventType.RECOMMENDATION_CREATED,
                AuditEventType.AGENT_RUN_FINISHED);
        verify(notifier).notifyNewRecommendations(any(), any());
    }

    @Test
    void inventedMoneyFiguresSetNeedsReview() throws Exception {
        String advice = TestFixtures.VALID_ADVICE.replace("$8,940.00", "$123,456.78");
        when(claude.generate(anyString(), anyString())).thenReturn(new AiResult(advice, "m", 1, 1));

        mvc.perform(post("/api/agents/cash-flow/run").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.needsReview").value(true))
                .andExpect(jsonPath("$.reviewNotes").value(org.hamcrest.Matchers.containsString("$123,456.78")))
                .andExpect(jsonPath("$.recommendations[0].needsReview").value(true));
    }

    @Test
    void providerFailureIs502AndRunIsRecordedAsFailed() throws Exception {
        when(claude.generate(anyString(), anyString()))
                .thenThrow(new AiProviderException("claude", AiProviderException.Kind.AUTHENTICATION, 401, null));

        String body = mvc.perform(post("/api/agents/cash-flow/run").header("Authorization", bearer()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andReturn().getResponse().getContentAsString();

        long runId = objectMapper.readTree(body).get("agentRunId").asLong();
        assertThat(runs.findById(runId).orElseThrow().getStatus()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(auditLog.findAll()).anyMatch(e -> e.getEventType() == AuditEventType.AGENT_RUN_FAILED
                && Long.valueOf(runId).equals(e.getAgentRunId()));
        verify(claude, org.mockito.Mockito.times(1)).generate(anyString(), anyString()); // no retry on bad key
        verify(notifier, never()).notifyNewRecommendations(any(), any());
    }

    @Test
    void invalidJsonTwiceIs502AndRunIsFailed() throws Exception {
        when(claude.generate(anyString(), anyString())).thenReturn(new AiResult("I think cash is fine.", "m", 1, 1));

        String body = mvc.perform(post("/api/agents/cash-flow/run").header("Authorization", bearer()))
                .andExpect(status().isBadGateway())
                .andReturn().getResponse().getContentAsString();

        long runId = objectMapper.readTree(body).get("agentRunId").asLong();
        assertThat(runs.findById(runId).orElseThrow().getStatus()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(recommendations.findByAgentRunIdOrderByIdAsc(runId)).isEmpty();
    }

    // ---------- recommendations ----------

    @Test
    void listFiltersByStatus() throws Exception {
        Recommendation rec = pendingRecommendation();
        String body = mvc.perform(get("/api/recommendations").param("status", "PENDING").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode list = objectMapper.readTree(body);
        assertThat(list.findValuesAsText("status")).containsOnly("PENDING");
        assertThat(list.findValues("id").stream().map(JsonNode::asLong)).contains(rec.getId());

        mvc.perform(get("/api/recommendations").param("status", "MAYBE").header("Authorization", bearer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void approveChangesStatusAndWritesAuditRow() throws Exception {
        Recommendation rec = pendingRecommendation();

        mvc.perform(post("/api/recommendations/{id}/approve", rec.getId()).header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.decidedBy").value("admin"));

        assertThat(recommendations.findById(rec.getId()).orElseThrow().getStatus())
                .isEqualTo(RecommendationStatus.APPROVED);
        assertThat(auditLog.findByRecommendationIdOrderByIdAsc(rec.getId()))
                .extracting(AuditLogEntry::getEventType, AuditLogEntry::getActor)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(AuditEventType.RECOMMENDATION_APPROVED, "admin"));
    }

    @Test
    void rejectWithCommentChangesStatusAndWritesAuditRow() throws Exception {
        Recommendation rec = pendingRecommendation();

        mvc.perform(post("/api/recommendations/{id}/reject", rec.getId()).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"Already handled\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.decisionComment").value("Already handled"));

        List<AuditLogEntry> entries = auditLog.findByRecommendationIdOrderByIdAsc(rec.getId());
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getEventType()).isEqualTo(AuditEventType.RECOMMENDATION_REJECTED);
        assertThat(entries.get(0).getDetails()).contains("Already handled");
    }

    @Test
    void decidingTwiceIs409AndUnknownIs404() throws Exception {
        Recommendation rec = pendingRecommendation();
        mvc.perform(post("/api/recommendations/{id}/approve", rec.getId()).header("Authorization", bearer()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/recommendations/{id}/reject", rec.getId()).header("Authorization", bearer()))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/recommendations/999999/approve").header("Authorization", bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void approvingDoesNotCallAnyExternalService() throws Exception {
        Recommendation rec = pendingRecommendation();
        clearInvocations(claude, gemini, notifier);

        mvc.perform(post("/api/recommendations/{id}/approve", rec.getId()).header("Authorization", bearer()))
                .andExpect(status().isOk());

        // No AI provider, no notifier: approval is a status change plus an audit row, nothing else.
        verifyNoInteractions(claude, gemini, notifier);
    }

    // ---------- audit log ----------

    @Test
    void auditLogIsReturnedNewestFirstWithLimit() throws Exception {
        Recommendation rec = pendingRecommendation();
        mvc.perform(post("/api/recommendations/{id}/approve", rec.getId()).header("Authorization", bearer()));

        mvc.perform(get("/api/audit-log").param("limit", "1").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].eventType").value("RECOMMENDATION_APPROVED"))
                .andExpect(jsonPath("$[0].recommendationId").value(rec.getId()));
    }
}
