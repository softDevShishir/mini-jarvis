package com.example.minijarvis.recommendation;

import java.time.Clock;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.minijarvis.audit.AuditEventType;
import com.example.minijarvis.audit.AuditService;

/**
 * Human decisions on recommendations. Approving or rejecting ONLY records the decision and an audit row.
 * Nothing is executed, and no external service is called: mini-jarvis-ai-agent is advisory-only.
 */
@Service
public class RecommendationService {

    private final RecommendationRepository repository;
    private final AuditService auditService;
    private final Clock clock;

    public RecommendationService(RecommendationRepository repository, AuditService auditService, Clock clock) {
        this.repository = repository;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Recommendation> list(RecommendationStatus status) {
        return status == null ? repository.findAllByOrderByIdDesc() : repository.findByStatusOrderByIdDesc(status);
    }

    @Transactional
    public Recommendation approve(long id, String actor, String comment) {
        return decide(id, RecommendationStatus.APPROVED, AuditEventType.RECOMMENDATION_APPROVED, actor, comment);
    }

    @Transactional
    public Recommendation reject(long id, String actor, String comment) {
        return decide(id, RecommendationStatus.REJECTED, AuditEventType.RECOMMENDATION_REJECTED, actor, comment);
    }

    private Recommendation decide(long id, RecommendationStatus decision, AuditEventType event, String actor,
            String comment) {
        Recommendation rec = repository.findById(id).orElseThrow(() -> new RecommendationNotFoundException(id));
        if (rec.getStatus() != RecommendationStatus.PENDING) {
            throw new RecommendationAlreadyDecidedException(id, rec.getStatus());
        }
        rec.decide(decision, actor, comment, clock.instant());
        auditService.record(event, actor, rec.getAgentRunId(), rec.getId(),
                "title=" + rec.getTitle() + (comment != null && !comment.isBlank() ? " comment=" + comment : ""));
        return rec;
    }
}
