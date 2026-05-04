package com.pipeline.xslttransformer.route;

import com.pipeline.xslttransformer.audit.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransformerRoute extends RouteBuilder {

    private final DynamicRouteManager dynamicRouteManager;
    private final AuditService auditService;

    @Value("${app.queue.input}")
    private String inputQueue;

    @Value("${app.queue.dlq}")
    private String dlqQueue;

    @Value("${app.queue.intermediate.prefix}")
    private String intermediatePrefix;

    @Override
    public void configure() throws Exception {

        onException(IllegalArgumentException.class,
                com.jayway.jsonpath.PathNotFoundException.class)
                .handled(true)
                .maximumRedeliveries(0)
                .process(exchange -> {
                    Exception e = exchange.getProperty(
                            org.apache.camel.Exchange.EXCEPTION_CAUGHT,
                            Exception.class);
                    String messageId = exchange.getIn()
                            .getHeader("MessageId", String.class);
                    String body = exchange.getIn().getBody(String.class);
                    auditService.auditError(
                            messageId, "UNKNOWN", inputQueue,
                            body, e.getMessage(),
                            auditService.getStackTrace(e));
                })
                .to("jms:" + dlqQueue);

        errorHandler(deadLetterChannel("jms:" + dlqQueue)
                .maximumRedeliveries(3)
                .redeliveryDelay(2000)
                .logExhausted(true));

        // ── CBR Route ─────────────────────────────────────────
        from("jms:" + inputQueue)
                .routeId("cbr-route")

                // Generate unique MessageId
                .process(exchange -> {
                    String messageId = UUID.randomUUID().toString();
                    exchange.getIn().setHeader("MessageId", messageId);
                })

                // ① ENTRY audit — Amq-json-Q1
                .process(exchange -> {
                    String messageId = exchange.getIn()
                            .getHeader("MessageId", String.class);
                    String body = exchange.getIn().getBody(String.class);
                    auditService.auditEntry(
                            messageId, "UNKNOWN", inputQueue, body);
                })
                .log("📨 [Amq-json-Q1] Received: ${body}")

                // Validate and extract Source
                .process(exchange -> {
                    String body = exchange.getIn().getBody(String.class);
                    String messageId = exchange.getIn()
                            .getHeader("MessageId", String.class);

                    String source;
                    try {
                        source = com.jayway.jsonpath.JsonPath
                                .read(body, "$.Source");
                    } catch (com.jayway.jsonpath.PathNotFoundException e) {
                        log.error("Missing 'Source' field: {}", body);
                        throw new IllegalArgumentException(
                                "Missing 'Source' field — sending to DLQ");
                    }

                    if (source == null || source.isBlank()) {
                        throw new IllegalArgumentException(
                                "'Source' field is empty — sending to DLQ");
                    }

                    exchange.getIn().setHeader("Source", source);

                    // Dynamically create route for this source if not exists
                    dynamicRouteManager.createRouteIfNotExists(source);
                })

                // ② EXIT audit — Amq-json-Q1
                .process(exchange -> {
                    String messageId = exchange.getIn()
                            .getHeader("MessageId", String.class);
                    String source = exchange.getIn()
                            .getHeader("Source", String.class);
                    String body = exchange.getIn().getBody(String.class);
                    auditService.auditExit(
                            messageId, source, inputQueue, body);
                })
                .log("✅ [Amq-json-Q1] Routed to: "
                        + intermediatePrefix + "${header.Source}")

                // Route to Amq-json-in-{Source}
                .toD("jms:" + intermediatePrefix + "${header.Source}");
    }
}