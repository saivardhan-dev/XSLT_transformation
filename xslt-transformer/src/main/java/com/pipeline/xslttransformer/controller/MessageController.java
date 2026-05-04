package com.pipeline.xslttransformer.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.jms.ConnectionFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
@Tag(name = "Message Sender",
        description = "Send messages to input queue")
public class MessageController {

    private final JmsTemplate jmsTemplate;

    @Value("${app.queue.input}")
    private String inputQueue;

    @Operation(summary = "Send message to input queue")
    @PostMapping("/send")
    public ResponseEntity<Map<String, String>> sendMessage(
            @RequestBody String payload) {
        try {
            jmsTemplate.convertAndSend(inputQueue, payload);
            log.info("Message sent to {}: {}", inputQueue, payload);
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "message", "Message sent to " + inputQueue
            ));
        } catch (Exception e) {
            log.error("Failed to send message: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                    "status", "FAILED",
                    "message", e.getMessage()
            ));
        }
    }
}