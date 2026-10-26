package com.example.minijarvis.agent;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Component;

@Component
public class AgentRegistry {

    private final Map<String, Agent> agents = new TreeMap<>();

    public AgentRegistry(List<Agent> agents) {
        for (Agent agent : agents) {
            if (this.agents.put(agent.name(), agent) != null) {
                throw new IllegalStateException("Duplicate agent name: " + agent.name());
            }
        }
    }

    public Agent get(String name) {
        Agent agent = agents.get(name);
        if (agent == null) {
            throw new AgentNotFoundException(name);
        }
        return agent;
    }

    public List<Agent> all() {
        return List.copyOf(agents.values());
    }
}
