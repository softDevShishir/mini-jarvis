package com.example.minijarvis.ai;

import java.time.Duration;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlock;
import com.example.minijarvis.config.JarvisProperties;

import jakarta.annotation.PreDestroy;

/** Claude via the official Anthropic Java SDK. */
@Component
public class ClaudeProvider implements AiProvider {

    public static final String ID = "claude";

    private final JarvisProperties.ProviderConfig config;
    private final int maxOutputTokens;
    private final Duration timeout;
    private volatile AnthropicClient client;

    public ClaudeProvider(JarvisProperties properties) {
        this.config = properties.ai().anthropic();
        this.maxOutputTokens = properties.ai().maxOutputTokens();
        this.timeout = Duration.ofSeconds(properties.ai().timeoutSeconds());
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String model() {
        return config.model();
    }

    @Override
    public boolean isAvailable() {
        return config.hasKey() && config.model() != null && !config.model().isBlank();
    }

    @Override
    public AiResult generate(String systemPrompt, String userPrompt) {
        if (!isAvailable()) {
            throw new AiProviderException(ID, AiProviderException.Kind.AUTHENTICATION, null, null);
        }
        try {
            MessageCreateParams params = MessageCreateParams.builder()
                    .model(config.model())
                    .maxTokens(maxOutputTokens)
                    .system(systemPrompt)
                    .addUserMessage(userPrompt)
                    .build();
            Message message = client().messages().create(params);
            String text = message.content().stream()
                    .map(ContentBlock::text)
                    .flatMap(java.util.Optional::stream)
                    .map(TextBlock::text)
                    .collect(Collectors.joining());
            return new AiResult(text, message.model().asString(),
                    message.usage().inputTokens(), message.usage().outputTokens());
        } catch (AnthropicServiceException e) {
            throw new AiProviderException(ID, AiProviderException.kindForStatus(e.statusCode()), e.statusCode(), e);
        } catch (AnthropicIoException e) {
            throw new AiProviderException(ID, AiProviderException.Kind.TEMPORARY, null, e);
        } catch (RuntimeException e) {
            throw new AiProviderException(ID, AiProviderException.Kind.UNKNOWN, null, e);
        }
    }

    private AnthropicClient client() {
        AnthropicClient c = client;
        if (c == null) {
            synchronized (this) {
                if (client == null) {
                    // SDK retries disabled: AiRouter owns retry/fallback policy.
                    client = AnthropicOkHttpClient.builder()
                            .apiKey(config.apiKey())
                            .maxRetries(0)
                            .timeout(timeout)
                            .build();
                }
                c = client;
            }
        }
        return c;
    }

    @PreDestroy
    void close() {
        if (client != null) {
            client.close();
        }
    }
}
