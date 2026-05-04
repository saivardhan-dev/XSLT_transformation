package com.pipeline.xslttransformer.audit.service;

import com.pipeline.xslttransformer.audit.model.AuditDocument;
import com.pipeline.xslttransformer.audit.repository.AuditRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditRepository auditRepository;

    // ── ENTRY audit ──────────────────────────────────────
    public void auditEntry(String messageId,
                           String source,
                           String queueName,
                           String payload) {
        save(AuditDocument.builder()
                .messageId(messageId)
                .source(source)
                .queueName(queueName)
                .eventType("ENTRY")
                .payload(payload)
                .timestamp(LocalDateTime.now())
                .build());
        log.info("✅ ENTRY audit saved | queue: {} | messageId: {}",
                queueName, messageId);
    }

    // ── EXIT audit ───────────────────────────────────────
    public void auditExit(String messageId,
                          String source,
                          String queueName,
                          String payload) {
        save(AuditDocument.builder()
                .messageId(messageId)
                .source(source)
                .queueName(queueName)
                .eventType("EXIT")
                .payload(payload)
                .timestamp(LocalDateTime.now())
                .build());
        log.info("✅ EXIT audit saved | queue: {} | messageId: {}",
                queueName, messageId);
    }

    // ── ERROR audit ──────────────────────────────────────
    public void auditError(String messageId,
                           String source,
                           String queueName,
                           String payload,
                           String errorMessage,
                           String stackTrace) {
        save(AuditDocument.builder()
                .messageId(messageId)
                .source(source)
                .queueName(queueName)
                .eventType("ERROR")
                .payload(payload)
                .timestamp(LocalDateTime.now())
                .errorMessage(errorMessage)
                .stackTrace(stackTrace)
                .build());
        log.error("❌ ERROR audit saved | queue: {} | messageId: {} | error: {}",
                queueName, messageId, errorMessage);
    }

    private void save(AuditDocument doc) {
        auditRepository.save(doc);
    }

    // ── Helper: extract stack trace as string ────────────
    public String getStackTrace(Exception e) {
        java.io.StringWriter sw = new java.io.StringWriter();
        e.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }
}