package com.example.minijarvis.audit;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLogEntry, Long> {

    List<AuditLogEntry> findAllByOrderByIdDesc(Pageable pageable);

    long countByEventTypeAndCreatedAtGreaterThanEqual(AuditEventType eventType, Instant from);

    List<AuditLogEntry> findByRecommendationIdOrderByIdAsc(Long recommendationId);
}
