package com.pipeline.xslttransformer.audit.repository;

import com.pipeline.xslttransformer.audit.model.AuditDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditRepository
        extends MongoRepository<AuditDocument, String> {

    List<AuditDocument> findByMessageId(String messageId);
    List<AuditDocument> findBySource(String source);
    List<AuditDocument> findByEventType(String eventType);
    List<AuditDocument> findByQueueName(String queueName);
    List<AuditDocument> findBySourceAndEventType(
            String source, String eventType);
}