package com.example.minijarvis.agent;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.minijarvis.ai.AiRouter;
import com.example.minijarvis.audit.AuditEventType;
import com.example.minijarvis.audit.AuditService;
import com.example.minijarvis.notification.RecommendationNotifier;
import com.example.minijarvis.recommendation.Recommendation;
import com.example.minijarvis.recommendation.RecommendationRepository;

/**
 * Runs an agent and owns everything around it: run record, audit trail, saving PENDING recommendations and the
 * optional notification. Agents themselves stay side-effect free.
 */
@Service
public class AgentRunService {

    private static final Logger log = LoggerFactory.getLogger(AgentRunService.class);

    private final AgentRegistry registry;
    private final AiRouter router;
    private final AgentRunRepository runs;
    private final RecommendationRepository recommendations;
    private final AuditService audit;
    private final RecommendationNotifier notifier;
    private final Clock clock;

    public AgentRunService(AgentRegistry registry, AiRouter router, AgentRunRepository runs,
            RecommendationRepository recommendations, AuditService audit, RecommendationNotifier notifier, Clock clock) {
        this.registry = registry;
        this.router = router;
        this.runs = runs;
        this.recommendations = recommendations;
        this.audit = audit;
        this.notifier = notifier;
        this.clock = clock;
    }

    public RunOutcome run(String agentName, String providerOverride, String actor) {
        Agent agent = registry.get(agentName);
        // Validate the provider up front: a bad ?provider= is a 400, not a failed run.
        String providerId = router.resolve(providerOverride).id();

        AgentRun run = runs.save(new AgentRun(agent.name(), actor, clock.instant()));
        audit.record(AuditEventType.AGENT_RUN_STARTED, actor, run.getId(), null,
                "agent=" + agent.name() + " provider=" + providerId);

        AgentResult result;
        try {
            result = agent.run(providerOverride);
        } catch (RuntimeException e) {
            log.warn("Agent '{}' run {} failed: {}", agent.name(), run.getId(), e.getMessage());
            run.fail(e.getMessage(), clock.instant());
            runs.save(run);
            audit.record(AuditEventType.AGENT_RUN_FAILED, actor, run.getId(), null,
                    "agent=" + agent.name() + " error=" + e.getClass().getSimpleName() + ": " + e.getMessage());
            throw new AgentRunFailedException(run.getId(), e);
        }

        List<Recommendation> saved = new ArrayList<>();
        for (AdviceItem item : result.recommendations()) {
            Recommendation rec = recommendations.save(new Recommendation(run.getId(), agent.name(), item.title(),
                    item.reason(), item.suggestedAction(), result.riskLevel(), result.provider(), result.model(),
                    result.fallbackUsed(), result.needsReview(), clock.instant()));
            audit.record(AuditEventType.RECOMMENDATION_CREATED, actor, run.getId(), rec.getId(),
                    "status=PENDING provider=" + result.provider() + " model=" + result.model()
                            + " fallbackUsed=" + result.fallbackUsed() + " needsReview=" + result.needsReview()
                            + " title=" + item.title());
            saved.add(rec);
        }

        run.succeed(result, clock.instant());
        runs.save(run);
        audit.record(AuditEventType.AGENT_RUN_FINISHED, actor, run.getId(), null,
                "agent=" + agent.name() + " provider=" + result.provider() + " model=" + result.model()
                        + " risk=" + result.riskLevel() + " recommendations=" + saved.size()
                        + " needsReview=" + result.needsReview() + " fallbackUsed=" + result.fallbackUsed()
                        + " tokensIn=" + result.inputTokens() + " tokensOut=" + result.outputTokens());

        if (!saved.isEmpty()) {
            try {
                notifier.notifyNewRecommendations(run, saved);
            } catch (RuntimeException e) {
                // Notifiers already swallow errors; this is a last line of defence so a run never fails here.
                log.warn("Notifier threw unexpectedly: {}", e.getClass().getSimpleName());
            }
        }
        return new RunOutcome(run, List.copyOf(saved));
    }

    public record RunOutcome(AgentRun run, List<Recommendation> recommendations) {
    }
}
