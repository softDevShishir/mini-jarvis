package com.example.minijarvis.recommendation;

import java.time.Instant;

import com.example.minijarvis.agent.RiskLevel;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "recommendations")
public class Recommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long agentRunId;
    private String agentName;
    private String title;
    private String reason;
    private String suggestedAction;

    @Enumerated(EnumType.STRING)
    private RiskLevel riskLevel;

    @Enumerated(EnumType.STRING)
    private RecommendationStatus status;

    private String provider;
    private String model;
    private boolean fallbackUsed;
    private boolean needsReview;
    private Instant createdAt;
    private Instant decidedAt;
    private String decidedBy;
    private String decisionComment;

    protected Recommendation() {
    }

    /** New recommendations are always PENDING; there is no other way to construct one. */
    public Recommendation(Long agentRunId, String agentName, String title, String reason, String suggestedAction,
            RiskLevel riskLevel, String provider, String model, boolean fallbackUsed, boolean needsReview,
            Instant createdAt) {
        this.agentRunId = agentRunId;
        this.agentName = agentName;
        this.title = truncate(title, 500);
        this.reason = truncate(reason, 4000);
        this.suggestedAction = truncate(suggestedAction, 4000);
        this.riskLevel = riskLevel;
        this.status = RecommendationStatus.PENDING;
        this.provider = provider;
        this.model = model;
        this.fallbackUsed = fallbackUsed;
        this.needsReview = needsReview;
        this.createdAt = createdAt;
    }

    void decide(RecommendationStatus decision, String actor, String comment, Instant at) {
        this.status = decision;
        this.decidedBy = actor;
        this.decisionComment = truncate(comment, 1000);
        this.decidedAt = at;
    }

    private static String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) : s;
    }

    public Long getId() { return id; }
    public Long getAgentRunId() { return agentRunId; }
    public String getAgentName() { return agentName; }
    public String getTitle() { return title; }
    public String getReason() { return reason; }
    public String getSuggestedAction() { return suggestedAction; }
    public RiskLevel getRiskLevel() { return riskLevel; }
    public RecommendationStatus getStatus() { return status; }
    public String getProvider() { return provider; }
    public String getModel() { return model; }
    public boolean isFallbackUsed() { return fallbackUsed; }
    public boolean isNeedsReview() { return needsReview; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDecidedAt() { return decidedAt; }
    public String getDecidedBy() { return decidedBy; }
    public String getDecisionComment() { return decisionComment; }
}
