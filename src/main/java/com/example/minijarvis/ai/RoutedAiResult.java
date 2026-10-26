package com.example.minijarvis.ai;

/** An AI result plus routing metadata: which provider actually answered and whether fallback kicked in. */
public record RoutedAiResult(AiResult result, String providerId, boolean fallbackUsed, int attempts) {
}
