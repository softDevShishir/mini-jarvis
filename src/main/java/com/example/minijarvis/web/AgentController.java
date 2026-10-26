package com.example.minijarvis.web;

import java.security.Principal;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.minijarvis.agent.Agent;
import com.example.minijarvis.agent.AgentRegistry;
import com.example.minijarvis.agent.AgentRunService;

@RestController
@RequestMapping("/api/agents")
public class AgentController {

    private final AgentRegistry registry;
    private final AgentRunService runService;

    public AgentController(AgentRegistry registry, AgentRunService runService) {
        this.registry = registry;
        this.runService = runService;
    }

    @GetMapping
    public List<String> list() {
        return registry.all().stream().map(Agent::name).toList();
    }

    /** Manual trigger. Produces PENDING recommendations only; nothing is executed. */
    @PostMapping("/{name}/run")
    public Dtos.AgentRunResponse run(@PathVariable String name,
            @RequestParam(required = false) String provider, Principal principal) {
        return Dtos.AgentRunResponse.from(runService.run(name, provider, principal.getName()));
    }
}
