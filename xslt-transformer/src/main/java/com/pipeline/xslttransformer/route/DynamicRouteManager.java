package com.pipeline.xslttransformer.route;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pipeline.xslttransformer.audit.service.AuditService;
import com.pipeline.xslttransformer.cache.XsltCacheManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.xml.transform.*;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class DynamicRouteManager {

    private final CamelContext camelContext;
    private final XsltCacheManager xsltCacheManager;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;    // ✅ inject instead of new

    private final Set<String> activeRoutes = ConcurrentHashMap.newKeySet();

    @Value("${app.queue.intermediate.prefix}")
    private String intermediatePrefix;

    @Value("${app.queue.output.prefix}")
    private String outputPrefix;

    @Value("${app.queue.dlq}")
    private String dlqQueue;

    public void createRouteIfNotExists(String source) throws Exception {

        String routeId     = "transformer-route-" + source;
        String inputQueue  = intermediatePrefix + source;
        String outputQueue = outputPrefix + source;

        if (activeRoutes.contains(routeId)) {
            log.info("Route already exists for source: {}", source);
            return;
        }

        log.info("🆕 Creating dynamic route for source: {}", source);

        camelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {

                // Don't retry data errors
                onException(IllegalArgumentException.class)
                        .handled(true)
                        .maximumRedeliveries(0)
                        .process(exchange -> {
                            Exception e = exchange.getProperty(
                                    org.apache.camel.Exchange.EXCEPTION_CAUGHT,
                                    Exception.class);
                            String messageId = exchange.getIn()
                                    .getHeader("MessageId", String.class);
                            String body = exchange.getIn()
                                    .getBody(String.class);
                            auditService.auditError(
                                    messageId, source, inputQueue,
                                    body, e.getMessage(),
                                    auditService.getStackTrace(e));
                        })
                        .to("jms:" + dlqQueue);

                // Retry system errors 3 times
                errorHandler(
                        deadLetterChannel("jms:" + dlqQueue)
                                .maximumRedeliveries(3)
                                .redeliveryDelay(2000)
                                .logExhausted(true)
                                .onExceptionOccurred(exchange -> {
                                    Exception e = exchange.getProperty(
                                            org.apache.camel.Exchange.EXCEPTION_CAUGHT,
                                            Exception.class);
                                    String messageId = exchange.getIn()
                                            .getHeader("MessageId", String.class);
                                    String body = exchange.getIn()
                                            .getBody(String.class);
                                    auditService.auditError(
                                            messageId, source, inputQueue,
                                            body, e.getMessage(),
                                            auditService.getStackTrace(e));
                                }));

                from("jms:" + inputQueue)
                        .routeId(routeId)

                        // ① ENTRY — Amq-json-in-SourceA
                        .process(exchange -> {
                            String messageId = exchange.getIn()
                                    .getHeader("MessageId", String.class);
                            String body = exchange.getIn()
                                    .getBody(String.class);
                            auditService.auditEntry(
                                    messageId, source, inputQueue, body);
                        })
                        .log("📨 [" + inputQueue + "] Message received")

                        // Transform
                        .process(exchange -> {
                            String body = exchange.getIn().getBody(String.class);
                            String xsltContent = xsltCacheManager.getXslt(source);
                            String xmlInput = convertJsonToXml(body);
                            String transformed = applyXslt(xmlInput, xsltContent);
                            exchange.getIn().setBody(transformed);
                        })

                        // ② EXIT — Amq-json-in-SourceA
                        .process(exchange -> {
                            String messageId = exchange.getIn()
                                    .getHeader("MessageId", String.class);
                            String body = exchange.getIn()
                                    .getBody(String.class);
                            auditService.auditExit(
                                    messageId, source, inputQueue, body);
                        })
                        .log("✅ [" + inputQueue + "] Transformed & consumed")

                        // ③ ENTRY — Amq-xml-out-SourceA
                        .process(exchange -> {
                            String messageId = exchange.getIn()
                                    .getHeader("MessageId", String.class);
                            String body = exchange.getIn()
                                    .getBody(String.class);
                            auditService.auditEntry(
                                    messageId, source, outputQueue, body);
                        })

                        // Deliver to output queue
                        .to("jms:" + outputQueue)

                        // ✅ NO EXIT audit after this — downstream owns it
                        .log("📤 [" + outputQueue + "] Message delivered ✅");
            }

            // ✅ Fixed — no deprecated fields()
            private String convertJsonToXml(String json) throws Exception {
                JsonNode node = objectMapper.readTree(json);
                StringBuilder xml = new StringBuilder("<root>");
                node.properties().forEach(entry ->       // ✅ properties() not fields()
                        xml.append("<").append(entry.getKey()).append(">")
                                .append(entry.getValue().asText())
                                .append("</").append(entry.getKey()).append(">"));
                xml.append("</root>");
                return xml.toString();
            }

            private String applyXslt(String xmlInput,
                                     String xsltContent) throws Exception {
                TransformerFactory factory =
                        TransformerFactory.newInstance();
                Source xsltSource = new StreamSource(
                        new StringReader(xsltContent));
                Transformer transformer =
                        factory.newTransformer(xsltSource);
                Source xmlSource = new StreamSource(
                        new StringReader(xmlInput));
                StringWriter writer = new StringWriter();
                transformer.transform(xmlSource,
                        new StreamResult(writer));
                return writer.toString();
            }
        });

        activeRoutes.add(routeId);
        log.info("✅ Dynamic route created for source: {}", source);
    }

    public Set<String> getActiveRoutes() {
        return activeRoutes;
    }
}