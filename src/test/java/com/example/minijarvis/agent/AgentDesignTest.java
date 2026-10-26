package com.example.minijarvis.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

/** Guards design rules H2 cannot catch. */
class AgentDesignTest {

    @Test
    void agentRunMustNotHoldATransactionAcrossTheAiCall() throws Exception {
        // A read-only transaction around run() makes the AI_CALL audit insert fail on Postgres.
        Method run = CashFlowAgent.class.getMethod("run", String.class);
        assertThat(run.isAnnotationPresent(Transactional.class)).isFalse();
        assertThat(CashFlowAgent.class.isAnnotationPresent(Transactional.class)).isFalse();
    }

    @Test
    void agentsDependOnlyOnTheProviderAbstraction() {
        for (var ctor : CashFlowAgent.class.getConstructors()) {
            for (Class<?> param : ctor.getParameterTypes()) {
                assertThat(param.getName()).doesNotStartWith("com.anthropic").doesNotStartWith("com.google.genai");
            }
        }
    }
}
