package com.example.minijarvis.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.minijarvis.audit.AuditService;

@RestController
@RequestMapping("/api/audit-log")
public class AuditLogController {

    private static final int MAX_LIMIT = 500;

    private final AuditService auditService;

    public AuditLogController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public List<Dtos.AuditLogDto> latest(@RequestParam(defaultValue = "50") int limit) {
        int bounded = Math.max(1, Math.min(limit, MAX_LIMIT));
        return auditService.latest(bounded).stream().map(Dtos.AuditLogDto::from).toList();
    }
}
