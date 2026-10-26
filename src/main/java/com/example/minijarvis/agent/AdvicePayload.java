package com.example.minijarvis.agent;

import java.util.List;

/** The validated JSON contract every advisory agent asks the model for. */
public record AdvicePayload(String summary, RiskLevel riskLevel, List<AdviceItem> recommendations) {
}
