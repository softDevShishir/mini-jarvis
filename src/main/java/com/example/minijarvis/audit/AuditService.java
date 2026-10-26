package com.example.minijarvis.audit;

import java.time.Clock;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private static final int MAX_DETAILS = 2000;

    private final AuditLogRepository repository;
    private final Clock clock;

    public AuditService(AuditLogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public AuditLogEntry record(AuditEventType type, String actor, Long agentRunId, Long recommendationId,
            String details) {
        String trimmed = details != null && details.length() > MAX_DETAILS ? details.substring(0, MAX_DETAILS) : details;
        return repository.save(new AuditLogEntry(type, actor, agentRunId, recommendationId, trimmed, clock.instant()));
    }

    public List<AuditLogEntry> latest(int limit) {
        return repository.findAllByOrderByIdDesc(PageRequest.of(0, limit));
    }
}
