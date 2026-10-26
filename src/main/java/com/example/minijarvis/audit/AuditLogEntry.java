package com.example.minijarvis.audit;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "audit_log")
public class AuditLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private AuditEventType eventType;

    private String actor;
    private Long agentRunId;
    private Long recommendationId;
    private String details;
    private Instant createdAt;

    protected AuditLogEntry() {
    }

    public AuditLogEntry(AuditEventType eventType, String actor, Long agentRunId, Long recommendationId,
            String details, Instant createdAt) {
        this.eventType = eventType;
        this.actor = actor;
        this.agentRunId = agentRunId;
        this.recommendationId = recommendationId;
        this.details = details;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public AuditEventType getEventType() { return eventType; }
    public String getActor() { return actor; }
    public Long getAgentRunId() { return agentRunId; }
    public Long getRecommendationId() { return recommendationId; }
    public String getDetails() { return details; }
    public Instant getCreatedAt() { return createdAt; }
}
