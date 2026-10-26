package com.example.minijarvis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * All runtime configuration, bound from application.yml (which reads env vars).
 * toString() is overridden on every record holding a secret so keys never end up in logs.
 */
@ConfigurationProperties(prefix = "jarvis")
public record JarvisProperties(
        String timezone,
        int maxAgentRunsPerDay,
        Ai ai,
        Security security,
        Scheduler scheduler,
        Telegram telegram,
        Business business) {

    public record Ai(
            String provider,
            String fallbackProvider,
            long retryBackoffMs,
            int maxOutputTokens,
            int timeoutSeconds,
            ProviderConfig anthropic,
            ProviderConfig gemini,
            ProviderConfig openai) {
    }

    public record ProviderConfig(String apiKey, String model) {
        public boolean hasKey() {
            return apiKey != null && !apiKey.isBlank();
        }

        @Override
        public String toString() {
            return "ProviderConfig[apiKey=" + (hasKey() ? "***" : "<unset>") + ", model=" + model + "]";
        }
    }

    public record Security(String adminUsername, String adminPassword, String jwtSecret, long jwtTtlMinutes) {
        @Override
        public String toString() {
            return "Security[adminUsername=" + adminUsername + ", adminPassword=***, jwtSecret=***, jwtTtlMinutes="
                    + jwtTtlMinutes + "]";
        }
    }

    public record Scheduler(boolean enabled, String cron) {
    }

    /** Describes the business to the agents, so the same agents work for any industry. */
    public record Business(String description, String currency) {
    }

    public record Telegram(String botToken, String chatId, String apiBaseUrl) {
        public boolean isConfigured() {
            return botToken != null && !botToken.isBlank() && chatId != null && !chatId.isBlank();
        }

        @Override
        public String toString() {
            return "Telegram[botToken=***, chatId=" + chatId + ", apiBaseUrl=" + apiBaseUrl + "]";
        }
    }
}
