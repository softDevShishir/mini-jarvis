package com.example.minijarvis.ai;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

import org.springframework.stereotype.Component;

import com.example.minijarvis.audit.AuditEventType;
import com.example.minijarvis.audit.AuditLogRepository;
import com.example.minijarvis.audit.AuditService;
import com.example.minijarvis.config.JarvisProperties;

/**
 * Caps AI calls per business day across all providers. Each call is recorded as an AI_CALL audit row, so the
 * count survives restarts. synchronized keeps check-and-record atomic within this instance.
 */
@Component
public class DailyAiCallLimiter {

    private final AuditLogRepository auditLogRepository;
    private final AuditService auditService;
    private final Clock clock;
    private final int maxPerDay;

    public DailyAiCallLimiter(AuditLogRepository auditLogRepository, AuditService auditService, Clock clock,
            JarvisProperties properties) {
        this.auditLogRepository = auditLogRepository;
        this.auditService = auditService;
        this.clock = clock;
        this.maxPerDay = properties.maxAgentRunsPerDay();
    }

    public synchronized void acquire(String providerId, String model) {
        Instant startOfDay = LocalDate.now(clock).atStartOfDay(clock.getZone()).toInstant();
        long used = auditLogRepository.countByEventTypeAndCreatedAtGreaterThanEqual(AuditEventType.AI_CALL, startOfDay);
        if (used >= maxPerDay) {
            throw new DailyLimitExceededException(maxPerDay);
        }
        auditService.record(AuditEventType.AI_CALL, "ai-router", null, null,
                "provider=" + providerId + " model=" + model + " callNumberToday=" + (used + 1) + "/" + maxPerDay);
    }
}
