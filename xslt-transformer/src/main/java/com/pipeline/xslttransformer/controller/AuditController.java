package com.pipeline.xslttransformer.controller;

import com.pipeline.xslttransformer.audit.model.AuditDocument;
import com.pipeline.xslttransformer.audit.repository.AuditRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/audits")
@RequiredArgsConstructor
@Tag(name = "Audit Logs",
        description = "View message audit logs from MongoDB")
public class AuditController {

    private final AuditRepository auditRepository;

    @Operation(summary = "Get all audit logs")
    @GetMapping
    public ResponseEntity<List<AuditDocument>> getAll() {
        return ResponseEntity.ok(auditRepository.findAll());
    }

    @Operation(summary = "Get audits by messageId",
            description = "Full audit trail for one message across all queues")
    @GetMapping("/message/{messageId}")
    public ResponseEntity<List<AuditDocument>> getByMessageId(
            @Parameter(description = "UUID of the message")
            @PathVariable String messageId) {
        return ResponseEntity.ok(
                auditRepository.findByMessageId(messageId));
    }

    @Operation(summary = "Get audits by source")
    @GetMapping("/source/{source}")
    public ResponseEntity<List<AuditDocument>> getBySource(
            @Parameter(description = "e.g. SourceA")
            @PathVariable String source) {
        return ResponseEntity.ok(
                auditRepository.findBySource(source));
    }

    @Operation(summary = "Get audits by queue name")
    @GetMapping("/queue/{queueName}")
    public ResponseEntity<List<AuditDocument>> getByQueue(
            @Parameter(description = "e.g. Amq-json-in-SourceA")
            @PathVariable String queueName) {
        return ResponseEntity.ok(
                auditRepository.findByQueueName(queueName));
    }

    @Operation(summary = "Get audits by event type",
            description = "ENTRY, EXIT or ERROR")
    @GetMapping("/event/{eventType}")
    public ResponseEntity<List<AuditDocument>> getByEventType(
            @Parameter(description = "ENTRY / EXIT / ERROR")
            @PathVariable String eventType) {
        return ResponseEntity.ok(
                auditRepository.findByEventType(eventType));
    }

    @Operation(summary = "Get all failed audits")
    @GetMapping("/failed")
    public ResponseEntity<List<AuditDocument>> getFailed() {
        return ResponseEntity.ok(
                auditRepository.findByEventType("ERROR"));
    }

    @Operation(summary = "Get audits by source and event type")
    @GetMapping("/source/{source}/event/{eventType}")
    public ResponseEntity<List<AuditDocument>> getBySourceAndEvent(
            @PathVariable String source,
            @PathVariable String eventType) {
        return ResponseEntity.ok(
                auditRepository.findBySourceAndEventType(
                        source, eventType));
    }
}