package com.example.minijarvis.ai;

import java.time.Duration;

import org.springframework.stereotype.Component;

import com.example.minijarvis.config.JarvisProperties;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;
import com.openai.models.ResponseFormatJsonObject;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.completions.CompletionUsage;

import jakarta.annotation.PreDestroy;

/** OpenAI via the official OpenAI Java SDK (Chat Completions), using JSON object response mode. */
@Component
public class OpenAiProvider implements AiProvider {

    public static final String ID = "openai";

    private final JarvisProperties.ProviderConfig config;
    private final int maxOutputTokens;
    private final Duration timeout;
    private volatile OpenAIClient client;

    public OpenAiProvider(JarvisProperties properties) {
        this.config = properties.ai().openai();
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
            ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                    .model(config.model())
                    .maxCompletionTokens(maxOutputTokens)
                    .responseFormat(ResponseFormatJsonObject.builder().build())
                    .addSystemMessage(systemPrompt)
                    .addUserMessage(userPrompt)
                    .build();
            ChatCompletion completion = client().chat().completions().create(params);
            // A refusal or a reasoning model that used its whole token budget yields no content; the parser
            // then treats the empty text as invalid output and retries once.
            String text = completion.choices().isEmpty() ? ""
                    : completion.choices().get(0).message().content().orElse("");
            long in = completion.usage().map(CompletionUsage::promptTokens).orElse(0L);
            long out = completion.usage().map(CompletionUsage::completionTokens).orElse(0L);
            return new AiResult(text, completion.model(), in, out);
        } catch (OpenAIServiceException e) {
            throw new AiProviderException(ID, AiProviderException.kindForStatus(e.statusCode()), e.statusCode(), e);
        } catch (OpenAIIoException e) {
            throw new AiProviderException(ID, AiProviderException.Kind.TEMPORARY, null, e);
        } catch (RuntimeException e) {
            throw new AiProviderException(ID, AiProviderException.Kind.UNKNOWN, null, e);
        }
    }

    private OpenAIClient client() {
        OpenAIClient c = client;
        if (c == null) {
            synchronized (this) {
                if (client == null) {
                    // SDK retries disabled: AiRouter owns retry/fallback policy.
                    client = OpenAIOkHttpClient.builder()
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
