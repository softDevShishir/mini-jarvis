package com.example.minijarvis.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Parses and validates the model's JSON:
 * { "summary": string, "risk_level": "LOW|MEDIUM|HIGH", "recommendations": [ {title, reason, suggested_action} ] }
 */
@Component
public class AdviceParser {

    static final int MAX_RECOMMENDATIONS = 10;
    private static final Pattern CODE_FENCE = Pattern.compile("^```[a-zA-Z]*\\s*\\n?(.*?)\\n?\\s*```$", Pattern.DOTALL);

    private final ObjectMapper objectMapper;

    public AdviceParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AdvicePayload parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidAdviceException("Model returned an empty response");
        }
        String json = stripCodeFences(raw);
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new InvalidAdviceException("Model response is not valid JSON", e);
        }
        if (root == null || !root.isObject()) {
            throw new InvalidAdviceException("Model response must be a JSON object");
        }

        String summary = requireText(root, "summary");
        String risk = requireText(root, "risk_level").toUpperCase(Locale.ROOT);
        RiskLevel riskLevel;
        try {
            riskLevel = RiskLevel.valueOf(risk);
        } catch (IllegalArgumentException e) {
            throw new InvalidAdviceException("risk_level must be LOW, MEDIUM or HIGH but was '" + risk + "'");
        }

        JsonNode recs = root.get("recommendations");
        if (recs == null || !recs.isArray()) {
            throw new InvalidAdviceException("recommendations must be an array");
        }
        if (recs.size() > MAX_RECOMMENDATIONS) {
            throw new InvalidAdviceException("Too many recommendations (" + recs.size() + " > " + MAX_RECOMMENDATIONS + ")");
        }
        List<AdviceItem> items = new ArrayList<>();
        for (int i = 0; i < recs.size(); i++) {
            JsonNode rec = recs.get(i);
            if (!rec.isObject()) {
                throw new InvalidAdviceException("recommendations[" + i + "] must be an object");
            }
            items.add(new AdviceItem(
                    requireText(rec, "title", "recommendations[" + i + "].title"),
                    requireText(rec, "reason", "recommendations[" + i + "].reason"),
                    requireText(rec, "suggested_action", "recommendations[" + i + "].suggested_action")));
        }
        return new AdvicePayload(summary, riskLevel, List.copyOf(items));
    }

    static String stripCodeFences(String raw) {
        String trimmed = raw.trim();
        Matcher m = CODE_FENCE.matcher(trimmed);
        return m.matches() ? m.group(1).trim() : trimmed;
    }

    private static String requireText(JsonNode node, String field) {
        return requireText(node, field, field);
    }

    private static String requireText(JsonNode node, String field, String path) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new InvalidAdviceException(path + " must be a non-empty string");
        }
        return value.asText().trim();
    }
}
