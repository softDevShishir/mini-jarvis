package com.example.minijarvis.ai;

/**
 * A large-language-model backend. Implementations must wrap every SDK error in {@link AiProviderException}
 * so nothing outside this package depends on a vendor SDK.
 */
public interface AiProvider {

    /** Stable lowercase id used in config and the API, e.g. "claude". */
    String id();

    /** The configured model name (from config, never hardcoded). */
    String model();

    /** True when the provider has the configuration (API key, model) it needs. */
    boolean isAvailable();

    AiResult generate(String systemPrompt, String userPrompt);
}
