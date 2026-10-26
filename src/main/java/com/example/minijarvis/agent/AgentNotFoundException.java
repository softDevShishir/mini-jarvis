package com.example.minijarvis.agent;

public class AgentNotFoundException extends RuntimeException {
    public AgentNotFoundException(String name) {
        super("Unknown agent '" + name + "'");
    }
}
