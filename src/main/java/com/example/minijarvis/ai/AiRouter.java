package com.example.minijarvis.ai;

import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.example.minijarvis.audit.AuditEventType;
import com.example.minijarvis.audit.AuditService;
import com.example.minijarvis.config.JarvisProperties;

/**
 * Chooses a provider (request override, else AI_PROVIDER), retries once on rate-limit/temporary errors,
 * then optionally falls back to AI_FALLBACK_PROVIDER. Auth and bad-request errors fail immediately.
 */
@Component
public class AiRouter {

    private static final Logger log = LoggerFactory.getLogger(AiRouter.class);

    private final List<AiProvider> providers;
    private final String defaultProviderId;
    private final String fallbackProviderId;
    private final long retryBackoffMs;
    private final DailyAiCallLimiter limiter;
    private final AuditService auditService;

    public AiRouter(List<AiProvider> providers, JarvisProperties properties, DailyAiCallLimiter limiter,
            AuditService auditService) {
        this.providers = List.copyOf(providers);
        this.defaultProviderId = normalize(properties.ai().provider());
        this.fallbackProviderId = normalize(properties.ai().fallbackProvider());
        this.retryBackoffMs = properties.ai().retryBackoffMs();
        this.limiter = limiter;
        this.auditService = auditService;
    }

    public List<AiProvider> providers() {
        return providers;
    }

    private AiProvider find(String id) {
        return providers.stream().filter(p -> id.equals(p.id())).findFirst().orElse(null);
    }

    public String defaultProviderId() {
        return defaultProviderId;
    }

    public String fallbackProviderId() {
        return fallbackProviderId;
    }

    /** Resolves and validates the provider to use. Throws {@link ProviderUnavailableException} (HTTP 400). */
    public AiProvider resolve(String providerOverride) {
        String id = normalize(providerOverride);
        if (id == null) {
            id = defaultProviderId;
        }
        if (id == null) {
            throw new ProviderUnavailableException("No AI provider selected. Set AI_PROVIDER or pass ?provider=");
        }
        AiProvider provider = find(id);
        if (provider == null) {
            throw new ProviderUnavailableException("Unknown AI provider '" + id + "'. Known providers: "
                    + String.join(", ", providers.stream().map(AiProvider::id).toList()));
        }
        if (!provider.isAvailable()) {
            throw new ProviderUnavailableException(
                    "AI provider '" + id + "' is not available: no API key or model configured");
        }
        return provider;
    }

    public RoutedAiResult generate(String providerOverride, String systemPrompt, String userPrompt) {
        AiProvider primary = resolve(providerOverride);
        int attempts = 0;
        AiProviderException lastError;
        try {
            attempts++;
            return new RoutedAiResult(call(primary, systemPrompt, userPrompt), primary.id(), false, attempts);
        } catch (AiProviderException e) {
            if (!e.isRetryable()) {
                throw e;
            }
            log.warn("AI provider '{}' failed with {} - retrying once in {} ms", primary.id(), e.getKind(), retryBackoffMs);
            lastError = e;
        }

        sleep(retryBackoffMs);
        try {
            attempts++;
            return new RoutedAiResult(call(primary, systemPrompt, userPrompt), primary.id(), false, attempts);
        } catch (AiProviderException e) {
            if (!e.isRetryable()) {
                throw e;
            }
            lastError = e;
        }

        AiProvider fallback = fallbackProviderId != null ? find(fallbackProviderId) : null;
        if (fallback == null || fallback == primary || !fallback.isAvailable()) {
            throw lastError;
        }
        log.warn("AI provider '{}' still failing ({}); using fallback '{}'", primary.id(), lastError.getKind(), fallback.id());
        auditService.record(AuditEventType.AI_FALLBACK_USED, "ai-router", null, null,
                "primary=" + primary.id() + " reason=" + lastError.getKind() + " fallback=" + fallback.id());
        attempts++;
        return new RoutedAiResult(call(fallback, systemPrompt, userPrompt), fallback.id(), true, attempts);
    }

    private AiResult call(AiProvider provider, String systemPrompt, String userPrompt) {
        limiter.acquire(provider.id(), provider.model());
        try {
            return provider.generate(systemPrompt, userPrompt);
        } catch (AiProviderException e) {
            throw e;
        } catch (RuntimeException e) {
            // Defensive: a provider leaked a non-wrapped exception.
            throw new AiProviderException(provider.id(), AiProviderException.Kind.UNKNOWN, null, e);
        }
    }

    private static void sleep(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String normalize(String id) {
        return id == null || id.isBlank() ? null : id.trim().toLowerCase(Locale.ROOT);
    }
}
