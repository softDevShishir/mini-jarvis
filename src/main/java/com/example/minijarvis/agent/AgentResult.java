package com.example.minijarvis.agent;

import java.util.List;

/** What an agent hands back. It is advice only; persisting and notifying is done by {@link AgentRunService}. */
public record AgentResult(
        String agentName,
        String summary,
        RiskLevel riskLevel,
        List<AdviceItem> recommendations,
        String provider,
        String model,
        boolean fallbackUsed,
        boolean needsReview,
        List<String> reviewNotes,
        long inputTokens,
        long outputTokens) {
}
