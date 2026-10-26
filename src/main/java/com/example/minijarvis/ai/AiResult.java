package com.example.minijarvis.ai;

public record AiResult(String text, String model, long inputTokens, long outputTokens) {
}
