package com.example.minijarvis.web;

import java.security.Principal;
import java.util.List;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.minijarvis.recommendation.RecommendationService;
import com.example.minijarvis.recommendation.RecommendationStatus;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/recommendations")
@Validated
public class RecommendationController {

    private final RecommendationService service;

    public RecommendationController(RecommendationService service) {
        this.service = service;
    }

    @GetMapping
    public List<Dtos.RecommendationDto> list(@RequestParam(required = false) RecommendationStatus status) {
        return service.list(status).stream().map(Dtos.RecommendationDto::from).toList();
    }

    /** Records the director's approval. Does NOT execute anything. */
    @PostMapping("/{id}/approve")
    public Dtos.RecommendationDto approve(@PathVariable long id,
            @Valid @RequestBody(required = false) Dtos.DecisionRequest body, Principal principal) {
        return Dtos.RecommendationDto.from(service.approve(id, principal.getName(), comment(body)));
    }

    @PostMapping("/{id}/reject")
    public Dtos.RecommendationDto reject(@PathVariable long id,
            @Valid @RequestBody(required = false) Dtos.DecisionRequest body, Principal principal) {
        return Dtos.RecommendationDto.from(service.reject(id, principal.getName(), comment(body)));
    }

    private static String comment(Dtos.DecisionRequest body) {
        return body == null ? null : body.comment();
    }
}
