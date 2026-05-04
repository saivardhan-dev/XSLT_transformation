package com.pipeline.xslttransformer.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.pipeline.xslttransformer.audit.service.AuditService;
import com.pipeline.xslttransformer.cache.XsltCacheManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.stereotype.Component;

import javax.xml.transform.*;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class XsltTransformProcessor implements Processor {

    private final XsltCacheManager xsltCacheManager;
    private final AuditService auditService;          // ✅ AuditService not AuditProcessor
    private final ObjectMapper objectMapper;           // ✅ inject not new ObjectMapper()

    @Override
    public void process(Exchange exchange) throws Exception {

        String jsonBody = exchange.getIn().getBody(String.class);

        // Generate unique MessageId
        String messageId = UUID.randomUUID().toString();
        exchange.getIn().setHeader("MessageId", messageId);

        // ── Validate Source field ──────────────────────────
        String source;
        try {
            source = JsonPath.read(jsonBody, "$.Source");
        } catch (com.jayway.jsonpath.PathNotFoundException e) {
            log.error("Missing 'Source' field in message: {}", jsonBody);
            auditService.auditError(                  // ✅ auditService
                    messageId,
                    "UNKNOWN",
                    "Amq-json-Q1",
                    jsonBody,
                    "Missing 'Source' field",
                    auditService.getStackTrace(e));
            throw new IllegalArgumentException(
                    "Missing required 'Source' field — sending to DLQ");
        }

        if (source == null || source.isBlank()) {
            log.error("'Source' field is empty in message: {}", jsonBody);
            auditService.auditError(
                    messageId,
                    "UNKNOWN",
                    "Amq-json-Q1",
                    jsonBody,
                    "'Source' field is empty",
                    "");
            throw new IllegalArgumentException(
                    "'Source' field cannot be empty — sending to DLQ");
        }

        log.info("Detected Source: {}", source);
        exchange.getIn().setHeader("Source", source);

        // ── Load XSLT from cache ───────────────────────────
        String xsltContent;
        try {
            xsltContent = xsltCacheManager.getXslt(source);
        } catch (RuntimeException e) {
            log.error("No XSLT found for Source: {}", source);
            auditService.auditError(                  // ✅ auditService
                    messageId,
                    source,
                    "Amq-json-Q1",
                    jsonBody,
                    e.getMessage(),
                    auditService.getStackTrace(e));
            throw e;
        }

        // ── Convert JSON → XML ─────────────────────────────
        String xmlInput = convertJsonToXml(jsonBody);
        log.info("Converted XML: {}", xmlInput);

        // ── Apply XSLT Transformation ──────────────────────
        String transformedOutput;
        try {
            transformedOutput = applyXslt(xmlInput, xsltContent);
            log.info("Transformed output: {}", transformedOutput);
        } catch (Exception e) {
            log.error("XSLT transformation failed for Source: {}", source);
            auditService.auditError(                  // ✅ auditService
                    messageId,
                    source,
                    "Amq-json-Q1",
                    jsonBody,
                    e.getMessage(),
                    auditService.getStackTrace(e));
            throw e;
        }

        exchange.getIn().setBody(transformedOutput);
    }

    // ✅ Fixed — no deprecated fields()
    private String convertJsonToXml(String json) throws Exception {
        JsonNode jsonNode = objectMapper.readTree(json);
        StringBuilder xml = new StringBuilder("<root>");
        jsonNode.properties().forEach(entry ->        // ✅ properties() not fields()
                xml.append("<").append(entry.getKey()).append(">")
                        .append(entry.getValue().asText())
                        .append("</").append(entry.getKey()).append(">"));
        xml.append("</root>");
        return xml.toString();
    }

    private String applyXslt(String xmlInput,
                             String xsltContent) throws Exception {
        TransformerFactory factory = TransformerFactory.newInstance();
        Source xsltSource = new StreamSource(new StringReader(xsltContent));
        Transformer transformer = factory.newTransformer(xsltSource);
        Source xmlSource = new StreamSource(new StringReader(xmlInput));
        StringWriter resultWriter = new StringWriter();
        transformer.transform(xmlSource, new StreamResult(resultWriter));
        return resultWriter.toString();
    }
}