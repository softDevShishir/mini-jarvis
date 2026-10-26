package com.example.minijarvis.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.example.minijarvis.ai.AiRouter;
import com.example.minijarvis.ai.RoutedAiResult;

/**
 * Shared helper for agents: calls the model through {@link AiRouter}, parses the advice JSON, and retries once
 * if the output is invalid. Agents use this instead of talking to providers directly.
 */
@Component
public class StructuredAdvisor {

    private static final Logger log = LoggerFactory.getLogger(StructuredAdvisor.class);

    static final String RETRY_SUFFIX = """


            IMPORTANT: Your previous reply was not valid JSON matching the required schema (%s).
            Return only valid JSON. No markdown, no code fences, no commentary.""";

    private final AiRouter router;
    private final AdviceParser parser;

    public StructuredAdvisor(AiRouter router, AdviceParser parser) {
        this.router = router;
        this.parser = parser;
    }

    public Response advise(String providerOverride, String systemPrompt, String userPrompt) {
        RoutedAiResult first = router.generate(providerOverride, systemPrompt, userPrompt);
        try {
            return new Response(parser.parse(first.result().text()), first,
                    first.result().inputTokens(), first.result().outputTokens());
        } catch (InvalidAdviceException e) {
            log.warn("Invalid advice JSON from '{}': {} - retrying once", first.providerId(), e.getMessage());
            RoutedAiResult second = router.generate(providerOverride, systemPrompt,
                    userPrompt + RETRY_SUFFIX.formatted(e.getMessage()));
            long in = first.result().inputTokens() + second.result().inputTokens();
            long out = first.result().outputTokens() + second.result().outputTokens();
            try {
                return new Response(parser.parse(second.result().text()), second, in, out);
            } catch (InvalidAdviceException e2) {
                throw new InvalidAdviceException("Model returned invalid JSON twice: " + e2.getMessage(), e2);
            }
        }
    }

    /** {@code routed} is the call that produced the accepted payload; token counts cover all calls. */
    public record Response(AdvicePayload payload, RoutedAiResult routed, long inputTokens, long outputTokens) {
    }
}
