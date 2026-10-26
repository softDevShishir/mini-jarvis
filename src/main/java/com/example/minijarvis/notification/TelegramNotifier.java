package com.example.minijarvis.notification;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.example.minijarvis.agent.AgentRun;
import com.example.minijarvis.audit.AuditEventType;
import com.example.minijarvis.audit.AuditService;
import com.example.minijarvis.config.JarvisProperties;
import com.example.minijarvis.recommendation.Recommendation;

/**
 * Sends one short Telegram message per agent run listing its new PENDING recommendations, via the Bot API
 * sendMessage endpoint. Skips silently when not configured; failures are logged and audited, never thrown.
 */
@Component
public class TelegramNotifier implements RecommendationNotifier {

    private static final Logger log = LoggerFactory.getLogger(TelegramNotifier.class);
    private static final int MAX_MESSAGE = 4000;

    private final JarvisProperties.Telegram config;
    private final RestClient restClient;
    private final AuditService audit;

    public TelegramNotifier(JarvisProperties properties, RestClient.Builder restClientBuilder, AuditService audit) {
        this.config = properties.telegram();
        this.restClient = restClientBuilder.baseUrl(config.apiBaseUrl()).build();
        this.audit = audit;
    }

    @Override
    public void notifyNewRecommendations(AgentRun run, List<Recommendation> recommendations) {
        if (!config.isConfigured()) {
            log.info("Telegram not configured (TELEGRAM_BOT_TOKEN/TELEGRAM_CHAT_ID); skipping notification for run {}",
                    run.getId());
            return;
        }
        String text = buildMessage(run, recommendations);
        try {
            restClient.post()
                    // Token inserted literally: template expansion would percent-encode its ':' separator.
                    .uri("/bot" + config.botToken() + "/sendMessage")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("chat_id", config.chatId(), "text", text))
                    .retrieve()
                    .toBodilessEntity();
            audit.record(AuditEventType.NOTIFICATION_SENT, "telegram", run.getId(), null,
                    "channel=telegram recommendations=" + recommendations.size());
        } catch (RuntimeException e) {
            // Exception messages can contain the request URL, which contains the bot token - redact it.
            String reason = redact(e.getClass().getSimpleName() + ": " + e.getMessage());
            log.warn("Telegram notification for run {} failed: {}", run.getId(), reason);
            audit.record(AuditEventType.NOTIFICATION_FAILED, "telegram", run.getId(), null, reason);
        }
    }

    static String buildMessage(AgentRun run, List<Recommendation> recommendations) {
        StringBuilder sb = new StringBuilder();
        sb.append("mini-jarvis: ").append(recommendations.size()).append(" new recommendation(s) from ")
                .append(run.getAgentName()).append(" awaiting your approval.\n");
        if (run.getRiskLevel() != null) {
            sb.append("Risk: ").append(run.getRiskLevel()).append('\n');
        }
        for (Recommendation rec : recommendations) {
            sb.append("\n#").append(rec.getId()).append(' ').append(rec.getTitle());
            if (rec.isNeedsReview()) {
                sb.append(" (check figures)");
            }
        }
        sb.append("\n\nNothing has been actioned. Approve or reject via the API.");
        return sb.length() > MAX_MESSAGE ? sb.substring(0, MAX_MESSAGE) : sb.toString();
    }

    private String redact(String s) {
        if (s == null) {
            return null;
        }
        if (config.botToken() == null || config.botToken().isBlank()) {
            return s;
        }
        return s.replace(config.botToken(), "***").replace(config.botToken().replace(":", "%3A"), "***");
    }
}
