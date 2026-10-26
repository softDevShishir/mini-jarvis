package com.example.minijarvis.ai;

import org.springframework.stereotype.Component;

import com.example.minijarvis.config.JarvisProperties;
import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.Part;

import jakarta.annotation.PreDestroy;

/** Gemini via the official Google Gen AI Java SDK, using JSON response mode. */
@Component
public class GeminiProvider implements AiProvider {

    public static final String ID = "gemini";

    private final JarvisProperties.ProviderConfig config;
    private final int maxOutputTokens;
    private volatile Client client;

    public GeminiProvider(JarvisProperties properties) {
        this.config = properties.ai().gemini();
        this.maxOutputTokens = properties.ai().maxOutputTokens();
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
            GenerateContentConfig generateConfig = GenerateContentConfig.builder()
                    .systemInstruction(Content.fromParts(Part.fromText(systemPrompt)))
                    .responseMimeType("application/json")
                    .maxOutputTokens(maxOutputTokens)
                    .build();
            GenerateContentResponse response = client().models.generateContent(config.model(), userPrompt, generateConfig);
            String text = response.text();
            long in = response.usageMetadata().flatMap(GenerateContentResponseUsageMetadata::promptTokenCount).orElse(0);
            long out = response.usageMetadata().flatMap(GenerateContentResponseUsageMetadata::candidatesTokenCount).orElse(0);
            return new AiResult(text == null ? "" : text, response.modelVersion().orElse(config.model()), in, out);
        } catch (ApiException e) {
            throw new AiProviderException(ID, classify(e), e.code(), e);
        } catch (GenAiIOException e) {
            throw new AiProviderException(ID, AiProviderException.Kind.TEMPORARY, null, e);
        } catch (RuntimeException e) {
            throw new AiProviderException(ID, AiProviderException.Kind.UNKNOWN, null, e);
        }
    }

    /** Gemini reports a bad key as HTTP 400 (API_KEY_INVALID); treat it as an auth error, not a bad request. */
    static AiProviderException.Kind classify(ApiException e) {
        String detail = (e.status() + " " + e.message()).toUpperCase(java.util.Locale.ROOT);
        if (e.code() == 400 && (detail.contains("API_KEY") || detail.contains("API KEY"))) {
            return AiProviderException.Kind.AUTHENTICATION;
        }
        return AiProviderException.kindForStatus(e.code());
    }

    private Client client() {
        Client c = client;
        if (c == null) {
            synchronized (this) {
                if (client == null) {
                    client = Client.builder().apiKey(config.apiKey()).build();
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
