package com.example.minijarvis.agent;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "agent_runs")
public class AgentRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String agentName;

    @Enumerated(EnumType.STRING)
    private AgentRunStatus status;

    private String triggeredBy;
    private String provider;
    private String model;
    private boolean fallbackUsed;
    private String summary;

    @Enumerated(EnumType.STRING)
    private RiskLevel riskLevel;

    private boolean needsReview;
    private String reviewNotes;
    private Long inputTokens;
    private Long outputTokens;
    private String errorMessage;
    private Instant startedAt;
    private Instant finishedAt;

    protected AgentRun() {
    }

    public AgentRun(String agentName, String triggeredBy, Instant startedAt) {
        this.agentName = agentName;
        this.triggeredBy = triggeredBy;
        this.startedAt = startedAt;
        this.status = AgentRunStatus.RUNNING;
    }

    public void succeed(AgentResult result, Instant finishedAt) {
        this.status = AgentRunStatus.SUCCEEDED;
        this.provider = result.provider();
        this.model = result.model();
        this.fallbackUsed = result.fallbackUsed();
        this.summary = truncate(result.summary(), 4000);
        this.riskLevel = result.riskLevel();
        this.needsReview = result.needsReview();
        this.reviewNotes = result.reviewNotes().isEmpty() ? null : truncate(String.join("\n", result.reviewNotes()), 2000);
        this.inputTokens = result.inputTokens();
        this.outputTokens = result.outputTokens();
        this.finishedAt = finishedAt;
    }

    public void fail(String errorMessage, Instant finishedAt) {
        this.status = AgentRunStatus.FAILED;
        this.errorMessage = truncate(errorMessage, 2000);
        this.finishedAt = finishedAt;
    }

    private static String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) : s;
    }

    public Long getId() { return id; }
    public String getAgentName() { return agentName; }
    public AgentRunStatus getStatus() { return status; }
    public String getTriggeredBy() { return triggeredBy; }
    public String getProvider() { return provider; }
    public String getModel() { return model; }
    public boolean isFallbackUsed() { return fallbackUsed; }
    public String getSummary() { return summary; }
    public RiskLevel getRiskLevel() { return riskLevel; }
    public boolean isNeedsReview() { return needsReview; }
    public String getReviewNotes() { return reviewNotes; }
    public Long getInputTokens() { return inputTokens; }
    public Long getOutputTokens() { return outputTokens; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
}
