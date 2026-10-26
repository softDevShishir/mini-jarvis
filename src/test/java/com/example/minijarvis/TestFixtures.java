package com.example.minijarvis;

import java.util.List;
import java.util.function.Function;

import com.example.minijarvis.ai.AiProvider;
import com.example.minijarvis.ai.AiProviderException;
import com.example.minijarvis.ai.AiResult;
import com.example.minijarvis.config.JarvisProperties;

public final class TestFixtures {

    public static final String VALID_ADVICE = """
            {
              "summary": "Cash is tight: $8,940.00 from Coastal Builders is overdue.",
              "risk_level": "HIGH",
              "recommendations": [
                { "title": "Chase Coastal Builders", "reason": "Largest overdue invoice", "suggested_action": "Call accounts payable" },
                { "title": "Delay equipment purchases", "reason": "Expenses exceed income", "suggested_action": "Review upcoming spend" }
              ]
            }""";

    private TestFixtures() {
    }

    public static JarvisProperties properties(String provider, String fallback) {
        return new JarvisProperties("Australia/Sydney", 100,
                new JarvisProperties.Ai(provider, fallback, 0, 1024, 30,
                        new JarvisProperties.ProviderConfig("claude-key", "claude-model"),
                        new JarvisProperties.ProviderConfig("gemini-key", "gemini-model"),
                        new JarvisProperties.ProviderConfig("openai-key", "openai-model")),
                new JarvisProperties.Security("admin", "pw", "x".repeat(40), 60, List.of()),
                new JarvisProperties.Scheduler(false, "0 0 7 * * *"),
                new JarvisProperties.Telegram("", "", "http://localhost"),
                new JarvisProperties.Business("a small test business", "AUD"));
    }

    /** Scriptable provider: each call is answered by {@code behaviour.apply(callNumber)} (1-based). */
    public static class FakeProvider implements AiProvider {
        private final String id;
        private final boolean available;
        private final Function<Integer, String> behaviour;
        public int calls;
        public String lastUserPrompt;

        public FakeProvider(String id, boolean available, Function<Integer, String> behaviour) {
            this.id = id;
            this.available = available;
            this.behaviour = behaviour;
        }

        public static FakeProvider answering(String id, String text) {
            return new FakeProvider(id, true, n -> text);
        }

        public static FakeProvider failing(String id, AiProviderException.Kind kind, Integer status) {
            return new FakeProvider(id, true, n -> {
                throw new AiProviderException(id, kind, status, null);
            });
        }

        @Override public String id() { return id; }
        @Override public String model() { return id + "-model"; }
        @Override public boolean isAvailable() { return available; }

        @Override
        public AiResult generate(String systemPrompt, String userPrompt) {
            calls++;
            lastUserPrompt = userPrompt;
            return new AiResult(behaviour.apply(calls), model(), 10, 20);
        }
    }
}
