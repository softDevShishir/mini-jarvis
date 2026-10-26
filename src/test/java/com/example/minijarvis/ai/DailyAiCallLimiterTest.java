package com.example.minijarvis.ai;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import com.example.minijarvis.TestFixtures;
import com.example.minijarvis.audit.AuditEventType;
import com.example.minijarvis.audit.AuditLogRepository;
import com.example.minijarvis.audit.AuditService;

class DailyAiCallLimiterTest {

    private final AuditLogRepository repo = mock(AuditLogRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final DailyAiCallLimiter limiter = new DailyAiCallLimiter(repo, audit,
            Clock.system(ZoneId.of("Australia/Sydney")), TestFixtures.properties("claude", "")); // cap = 100

    @Test
    void allowsAndRecordsCallsUnderTheCap() {
        when(repo.countByEventTypeAndCreatedAtGreaterThanEqual(eq(AuditEventType.AI_CALL), any())).thenReturn(99L);
        assertThatCode(() -> limiter.acquire("claude", "m")).doesNotThrowAnyException();
        verify(audit).record(eq(AuditEventType.AI_CALL), any(), any(), any(), any());
    }

    @Test
    void blocksAtTheCap() {
        when(repo.countByEventTypeAndCreatedAtGreaterThanEqual(eq(AuditEventType.AI_CALL), any())).thenReturn(100L);
        assertThatThrownBy(() -> limiter.acquire("gemini", "m")).isInstanceOf(DailyLimitExceededException.class);
        verify(audit, never()).record(any(), any(), any(), any(), any());
    }
}
