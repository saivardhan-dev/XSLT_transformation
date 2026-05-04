# XSLT Transformation Platform

A message transformation pipeline built with **Apache Camel**, **Spring Boot**, **ActiveMQ**, and **MongoDB**. The platform receives JSON messages from a source queue, dynamically routes them based on their source type, transforms them to XML using externally managed XSLT files, and delivers the output to destination queues — with full audit logging and a web dashboard.

---

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Queue Design](#queue-design)
- [Message Format](#message-format)
- [XSLT Files](#xslt-files)
- [Audit Logging](#audit-logging)
- [Cache Management](#cache-management)
- [File Watcher](#file-watcher)
- [REST APIs](#rest-apis)
- [Dashboard](#dashboard)
- [Getting Started](#getting-started)
- [Configuration](#configuration)
- [Adding a New Source](#adding-a-new-source)

---

## Overview

The XSLT Transformation Platform solves the problem of transforming heterogeneous JSON messages from multiple sources into standardized XML output — without requiring code changes or application restarts when new message types are introduced.

Key design goals:

- **Zero code changes** when adding new message sources
- **Dynamic routing** based on message content
- **External XSLT management** — transformation rules live outside the application
- **Automatic cache invalidation** when XSLT files are updated on disk
- **Full audit trail** for every message across every queue

---

## Architecture

```
Producer
    │
    ▼
Amq-json-Q1  (master input queue)
    │
    ▼
Apache Camel — CBR Route
    │  reads "Source" field from JSON
    │  creates dynamic route if new source
    │
    ▼
Amq-json-in-{Source}  (intermediate queue per source)
    │
    ▼
Apache Camel — Transformer Route
    │  loads {Source}.xslt from cache (or disk on first call)
    │  converts JSON → XML
    │  applies XSLT transformation
    │
    ├──► Amq-xml-out-{Source}  (output queue per source)
    │
    └──► Amq-DLQ  (on failure)

MongoDB — audit trail saved at every step
```

---

## Tech Stack

| Component | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 4.x |
| Integration | Apache Camel 4.10 |
| Message Broker | Apache ActiveMQ |
| Database | MongoDB |
| Transformation | XSLT 1.0 |
| API Docs | SpringDoc OpenAPI (Swagger) |
| Dashboard | HTML + Vanilla JS |

---

## Project Structure

```
xslt-transformer/
├── src/main/java/com/pipeline/xslttransformer/
│   ├── XsltTransformerApplication.java
│   ├── config/
│   │   ├── ActiveMQConfig.java       ← JMS connection factory
│   │   ├── AppConfig.java            ← ObjectMapper bean
│   │   ├── CorsConfig.java           ← CORS for dashboard
│   │   └── SwaggerConfig.java        ← OpenAPI config
│   ├── route/
│   │   ├── TransformerRoute.java     ← CBR route (Amq-json-Q1)
│   │   └── DynamicRouteManager.java  ← creates routes at runtime
│   ├── processor/
│   │   └── XsltTransformProcessor.java ← validates, transforms
│   ├── cache/
│   │   └── XsltCacheManager.java     ← in-memory XSLT cache
│   ├── watcher/
│   │   └── XsltFileWatcher.java      ← monitors XSLT folder
│   ├── audit/
│   │   ├── AuditService.java         ← saves audit events
│   │   └── model/
│   │       └── AuditDocument.java    ← MongoDB document
│   └── controller/
│       ├── MessageController.java    ← POST /api/messages/send
│       ├── AuditController.java      ← GET /api/audits
│       └── CacheController.java      ← DELETE /api/cache/evict
│
├── resources/
│   └── application.properties
│
XSLT_Files/                           ← external XSLT directory
│   ├── SourceA.xslt
│   ├── SourceB.xslt
│   └── {Source}.xslt
│
xslt-ui/
│   └── index.html                    ← web dashboard
```

---

## Queue Design

| Queue | Role | Created By |
|---|---|---|
| `Amq-json-Q1` | Master input — all messages arrive here | Manual / Producer |
| `Amq-json-in-{Source}` | Intermediate per source e.g. `Amq-json-in-SourceA` | Auto at runtime |
| `Amq-xml-out-{Source}` | Transformed XML output per source | Auto at runtime |
| `Amq-DLQ` | Failed messages | Auto on error |

---

## Message Format

Every message sent to `Amq-json-Q1` must include a `Source` field. This field drives routing, XSLT selection, and queue naming.

**Example — SourceA:**
```json
{
  "Source": "SourceA",
  "Item": "itemA",
  "Price": "PriceA",
  "Location": "LocationA",
  "Store": "StoreA"
}
```

**Example — SourceB:**
```json
{
  "Source": "SourceB",
  "Brand": "Sony",
  "Category": "Electronics",
  "Price": "1200"
}
```

If the `Source` field is missing or empty, the message is immediately sent to `Amq-DLQ` with no retries.

---

## XSLT Files

XSLT files live **outside the application** in a configurable external directory. This means transformation rules can be updated without touching code or restarting the app.

**Naming convention:** `{Source}.xslt`

Example `SourceA.xslt`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <xsl:output method="xml" indent="yes"/>

    <xsl:template match="/">
        <SourceA>
            <Source><xsl:value-of select="//Source"/></Source>
            <Item><xsl:value-of select="//Item"/></Item>
            <Price><xsl:value-of select="//Price"/></Price>
            <Location><xsl:value-of select="//Location"/></Location>
            <Store><xsl:value-of select="//Store"/></Store>
        </SourceA>
    </xsl:template>

</xsl:stylesheet>
```

**Transformation flow:**
```
JSON → convertJsonToXml() → intermediate XML → applyXslt() → transformed XML
```

---

## Audit Logging

Every message generates **5 audit events** stored in MongoDB collection `message_audits`:

| Step | Event | Queue |
|---|---|---|
| ① | ENTRY | `Amq-json-Q1` |
| ② | EXIT | `Amq-json-Q1` |
| ③ | ENTRY | `Amq-json-in-{Source}` |
| ④ | EXIT | `Amq-json-in-{Source}` |
| ⑤ | ENTRY | `Amq-xml-out-{Source}` |

On failure an additional **ERROR** audit is saved with the error message and full stack trace.

**Audit document structure:**
```json
{
  "messageId": "uuid",
  "source": "SourceA",
  "queueName": "Amq-json-in-SourceA",
  "eventType": "EXIT",
  "payload": "<SourceA>...</SourceA>",
  "timestamp": "2026-05-01T10:00:00",
  "errorMessage": null,
  "stackTrace": null
}
```

---

## Cache Management

XSLT files are loaded from disk on the **first message** for each source and cached in-memory using a `ConcurrentHashMap`. Subsequent messages for the same source are served from cache with no disk I/O.

**Cache lifecycle:**
```
First message for SourceA
    → Cache MISS → load SourceA.xslt from disk → cache it

Second message for SourceA
    → Cache HIT → serve from cache

SourceA.xslt updated on disk
    → File Watcher detects change → evicts SourceA from cache

Next SourceA message
    → Cache MISS → loads updated file → caches new version
```

Cache can also be manually managed via REST API or the dashboard.

---

## File Watcher

A background thread using Java's `WatchService` monitors the external XSLT directory for file changes:

| File Event | Action |
|---|---|
| File modified | Evicts that source from cache |
| New file added | Logs it — will load lazily on first message |
| File deleted | Evicts that source from cache |

This means updating an XSLT file on disk automatically takes effect on the next message — no restart needed.

---

## REST APIs

Full API documentation available at `http://localhost:8080/swagger-ui.html`

### Messages
| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/messages/send` | Send a JSON message to `Amq-json-Q1` |

### Audits
| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/audits` | Get all audit logs |
| GET | `/api/audits/source/{source}` | Get audits by source |
| GET | `/api/audits/event/{eventType}` | Get audits by ENTRY / EXIT / ERROR |
| GET | `/api/audits/message/{messageId}` | Full trail for one message |
| GET | `/api/audits/queue/{queueName}` | Get audits by queue name |
| GET | `/api/audits/failed` | Get all error audits |

### Cache
| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/cache/status` | View all cached sources and active routes |
| GET | `/api/cache/status/{source}` | Check if specific source is cached |
| DELETE | `/api/cache/evict/{source}` | Evict a specific source from cache |
| DELETE | `/api/cache/evict/all` | Clear entire cache |

---

## Dashboard

A single-page web dashboard (`xslt-ui/index.html`) provides a unified interface for the platform.

**Tabs:**

| Tab | Features |
|---|---|
| Send & Transform | Paste JSON payload, click Transform, see live audit trail and transformed XML |
| Audit Logs | View all MongoDB audits with filter by source, event type. Full message IDs and timestamps |
| Queues | Direct links to each queue in the ActiveMQ console |
| Cache | View cached XSLT files, evict individual or all entries, view active Camel routes |
| API Docs ↗ | Opens Swagger UI in a new tab |

**To run the dashboard:**
```bash
cd xslt-ui
python3 -m http.server 3000
# Open http://localhost:3000
```

---

## Getting Started

### Prerequisites

- Java 17+
- Maven 3.x
- Apache ActiveMQ running on `localhost:61616`
- MongoDB running on `localhost:27017`

### 1. Clone and build

```bash
cd xslt-transformer
mvn clean install
```

### 2. Configure external XSLT path

Update `application.properties`:
```properties
app.xslt.external-path=/your/path/to/XSLT_Files/
```

### 3. Add XSLT files

Create `{Source}.xslt` files in your external XSLT directory.

### 4. Run the application

```bash
mvn spring-boot:run
```

### 5. Run the dashboard

```bash
cd xslt-ui
python3 -m http.server 3000
```

### 6. Open the dashboard

```
http://localhost:3000
```

---

## Configuration

All configuration lives in `src/main/resources/application.properties`:

```properties
# ActiveMQ
spring.activemq.broker-url=tcp://localhost:61616
spring.activemq.user=admin
spring.activemq.password=admin

# MongoDB
spring.data.mongodb.uri=mongodb://localhost:27017/xslt_audit

# Queue Names
app.queue.input=Amq-json-Q1
app.queue.dlq=Amq-DLQ
app.queue.intermediate.prefix=Amq-json-in-
app.queue.output.prefix=Amq-xml-out-

# External XSLT path
app.xslt.external-path=/path/to/XSLT_Files/

# Swagger
springdoc.swagger-ui.path=/swagger-ui.html
springdoc.swagger-ui.enabled=true
```

---

## Adding a New Source

Adding a new message source requires **zero code changes**:

1. Create the XSLT file:
```bash
# Create SourceC.xslt in your external XSLT folder
touch /path/to/XSLT_Files/SourceC.xslt
```

2. Write the transformation rules in `SourceC.xslt`

3. Send a message with `"Source": "SourceC"` to `Amq-json-Q1`

The application will automatically:
- Detect the new source from the message
- Create a dynamic Camel route for `Amq-json-in-SourceC`
- Load and cache `SourceC.xslt` from disk
- Transform and deliver to `Amq-xml-out-SourceC`
- Audit all 5 events in MongoDB

No restart. No config change. No code change. ✅
