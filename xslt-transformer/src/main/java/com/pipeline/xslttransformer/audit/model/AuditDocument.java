package com.pipeline.xslttransformer.audit.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "message_audits")
public class AuditDocument {

    @Id
    private String id;

    private String messageId;        // unique ID per message
    private String source;           // SourceA, SourceB etc.
    private String queueName;        // which queue this audit is for
    private String eventType;        // ENTRY / EXIT / ERROR
    private String payload;          // message content at this stage
    private LocalDateTime timestamp; // when this audit event happened
    private String errorMessage;     // null if success
    private String stackTrace;       // null if success
}