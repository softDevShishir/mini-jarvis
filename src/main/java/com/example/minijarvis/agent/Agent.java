package com.example.minijarvis.agent;

/**
 * An advisory agent. Agents read business data and ask the model for advice; they never perform actions and
 * never write recommendations themselves. Register a new agent by making it a Spring {@code @Component}.
 */
public interface Agent {

    /** URL-safe unique name, e.g. "cash-flow". */
    String name();

    /**
     * @param providerOverride "claude", "gemini" or null for the configured default
     */
    AgentResult run(String providerOverride);
}
