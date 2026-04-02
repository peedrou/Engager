# Engager

**An event-driven user segmentation service built in Scala** — inspired by
[Iterable's](https://iterable.com) production architecture.

Engager demonstrates a real-world data pipeline: events flow in via REST API,
get published to Kafka, are processed by Akka Streams, indexed into
Elasticsearch for fast user segmentation, and trigger campaigns when users
match defined segments.

## Architecture

```
Clients → REST API (Akka HTTP) → Kafka → Akka Streams → ES / Postgres → Campaign Trigger → Webhooks
```

## Tech Stack

| Component         | Technology                 |
| ----------------- | -------------------------- |
| Language          | Scala 2.13                 |
| HTTP Framework    | Akka HTTP                  |
| Stream Processing | Akka Streams               |
| Message Broker    | Apache Kafka               |
| Search Engine     | Elasticsearch 8.x          |
| Relational DB     | PostgreSQL 16              |
| JSON              | circe                      |
| Containerization  | Docker Compose             |
| Testing           | ScalaTest + Testcontainers |

## Quick Start

### Prerequisites

- JDK 11+ (recommended: 17)
- [sbt](https://www.scala-sbt.org/download.html) 1.9+
- Docker & Docker Compose

### 1. Start infrastructure

```bash
docker-compose up -d
```

### 2. Run the application

```bash
sbt run
```

API starts at `http://localhost:8080`.

### 3. Test it

```bash
# Health check
curl http://localhost:8080/health

# Ingest an event
curl -X POST http://localhost:8080/api/v1/events \
  -H "Content-Type: application/json" \
  -d '{"userId":"user-123","eventType":"PURCHASE","properties":{"amount":49.99}}'
```
