package com.example.minijarvis.scheduling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.example.minijarvis.agent.Agent;
import com.example.minijarvis.agent.AgentRegistry;
import com.example.minijarvis.agent.AgentRunService;

/** Runs every agent on SCHEDULER_CRON. Only exists when SCHEDULER_ENABLED=true, so no credits are spent by default. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "jarvis.scheduler", name = "enabled", havingValue = "true")
public class AgentScheduler {

    private static final Logger log = LoggerFactory.getLogger(AgentScheduler.class);

    private final AgentRegistry registry;
    private final AgentRunService runService;

    public AgentScheduler(AgentRegistry registry, AgentRunService runService) {
        this.registry = registry;
        this.runService = runService;
    }

    @Scheduled(cron = "${jarvis.scheduler.cron}", zone = "${jarvis.timezone}")
    public void runAll() {
        for (Agent agent : registry.all()) {
            try {
                runService.run(agent.name(), null, "scheduler");
            } catch (RuntimeException e) {
                // Already recorded as a FAILED run where applicable; keep going with the other agents.
                log.warn("Scheduled run of '{}' failed: {}", agent.name(), e.getMessage());
            }
        }
    }
}
