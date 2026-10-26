package com.example.minijarvis.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.minijarvis.ai.AiRouter;

/** Reports provider availability. Never exposes API keys. */
@RestController
@RequestMapping("/api/providers")
public class ProviderController {

    private final AiRouter router;

    public ProviderController(AiRouter router) {
        this.router = router;
    }

    @GetMapping
    public Dtos.ProvidersResponse list() {
        var providers = router.providers().stream()
                .map(p -> new Dtos.ProviderInfo(p.id(), p.model(), p.isAvailable(),
                        p.id().equals(router.defaultProviderId()), p.id().equals(router.fallbackProviderId())))
                .toList();
        return new Dtos.ProvidersResponse(router.defaultProviderId(), router.fallbackProviderId(), providers);
    }
}
