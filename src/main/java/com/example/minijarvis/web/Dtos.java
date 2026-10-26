package com.example.minijarvis.web;

import java.time.Instant;
import java.util.List;

import com.example.minijarvis.agent.AgentRun;
import com.example.minijarvis.agent.AgentRunService;
import com.example.minijarvis.audit.AuditLogEntry;
import com.example.minijarvis.recommendation.Recommendation;

import jakarta.validation.constraints.Size;

/** API response/request shapes, kept separate from JPA entities. */
public final class Dtos {

    private Dtos() {
    }

    public record ProviderInfo(String id, String model, boolean available, boolean isDefault, boolean isFallback) {
    }

    public record ProvidersResponse(String defaultProvider, String fallbackProvider, List<ProviderInfo> providers) {
    }

    public record RecommendationDto(Long id, Long agentRunId, String agentName, String title, String reason,
            String suggestedAction, String riskLevel, String status, String provider, String model,
            boolean fallbackUsed, boolean needsReview, Instant createdAt, Instant decidedAt, String decidedBy,
            String decisionComment) {

        public static RecommendationDto from(Recommendation r) {
            return new RecommendationDto(r.getId(), r.getAgentRunId(), r.getAgentName(), r.getTitle(), r.getReason(),
                    r.getSuggestedAction(), r.getRiskLevel().name(), r.getStatus().name(), r.getProvider(),
                    r.getModel(), r.isFallbackUsed(), r.isNeedsReview(), r.getCreatedAt(), r.getDecidedAt(),
                    r.getDecidedBy(), r.getDecisionComment());
        }
    }

    public record AgentRunResponse(Long runId, String agent, String status, String provider, String model,
            boolean fallbackUsed, String summary, String riskLevel, boolean needsReview, String reviewNotes,
            Long inputTokens, Long outputTokens, Instant startedAt, Instant finishedAt,
            List<RecommendationDto> recommendations) {

        public static AgentRunResponse from(AgentRunService.RunOutcome outcome) {
            AgentRun run = outcome.run();
            return new AgentRunResponse(run.getId(), run.getAgentName(), run.getStatus().name(), run.getProvider(),
                    run.getModel(), run.isFallbackUsed(), run.getSummary(),
                    run.getRiskLevel() == null ? null : run.getRiskLevel().name(), run.isNeedsReview(),
                    run.getReviewNotes(), run.getInputTokens(), run.getOutputTokens(), run.getStartedAt(),
                    run.getFinishedAt(), outcome.recommendations().stream().map(RecommendationDto::from).toList());
        }
    }

    public record AuditLogDto(Long id, String eventType, String actor, Long agentRunId, Long recommendationId,
            String details, Instant createdAt) {

        public static AuditLogDto from(AuditLogEntry e) {
            return new AuditLogDto(e.getId(), e.getEventType().name(), e.getActor(), e.getAgentRunId(),
                    e.getRecommendationId(), e.getDetails(), e.getCreatedAt());
        }
    }

    public record DecisionRequest(@Size(max = 1000) String comment) {
    }
}
