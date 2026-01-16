🏭 Factory Event Ingestion System

A high-performance, thread-safe backend service designed to ingest machine sensor events from factory equipment and provide real-time statistical reporting.

📖 Table of Contents

Architecture

Deduplication & Update Logic

Thread Safety

Data Model

Performance Strategy

Tested Scenarios (Edge Cases)

Setup & Run Instructions

Future Improvements

1. 🏗️ Architecture

The system follows a modular Monolithic architecture designed for simplicity and speed.

1.1 API Layer (EventController)

The entry point. It handles HTTP requests, validates JSON structures, and delegates processing to the Service layer. It ensures strict typing for:

POST /events/batch: Batch ingestion.

GET /stats: Statistical reporting.

1.2 Service Layer (EventService)

The brain of the operation. Responsible for:

Validation: Rejects invalid durations (>6hrs) or future timestamps.

Logic: Handles deduplication and conflict resolution.

Batching: Orchestrates the bulk processing flow.

1.3 Data Layer (MachineEventRepository)

Built on Spring Data JPA with H2 In-Memory Database.

Why H2? It fulfills the requirement to "run locally without installation" while strictly supporting SQL, ACID transactions, and Indexes for performance.

2. 🧠 Deduplication & Update Logic

To handle duplicate transmissions and out-of-order delivery, we utilize a "Bulk Read-Modify-Write" strategy.

The Strategy (3-Phase Process)

Bulk Fetch: Extract all eventIds from the batch and query the DB once. (Reduces N queries to 1).

In-Memory Comparison: Compare incoming events against existing records.

Conflict Resolution:

Scenario

Condition

Action

Duplicate

ID exists + Payload Identical

Ignore (Dedupe)

Newer Data

ID exists + Payload differs + Incoming time is Newer

Update (Last-Write-Wins)

Older Data

ID exists + Payload differs + Incoming time is Older

Ignore (Obsolete)

New Event

ID does not exist

Insert

⚡ Critical Edge Case: Intra-Batch Race Conditions

If a single batch contains multiple updates for the same eventId, a standard loop would fail.

Solution: We maintain a real-time local map during processing. As soon as an event is processed, the map is updated. Subsequent events in the same batch compare themselves against this "live" state.

3. 🛡️ Thread Safety

We enforce safety for concurrent requests at multiple levels:

✅ Database Transactions (@Transactional): The entire batch runs atomically. Isolation levels prevent dirty reads.

✅ Unique Constraints: The database strictly enforces UNIQUE(eventId). Even if application logic fails, the DB rejects duplicate inserts.

✅ Stateless Design: No mutable state is shared between request threads in Java memory.

4. 📊 Data Model

Data is stored in a single optimized table: machine_events.

Field

Type

Description

Index Strategy

eventId

String (PK)

Unique Identifier

Primary Key (O(1) lookups)

machineId

String

Source Machine

Composite Index

eventTime

Timestamp

Sensor Time

Composite Index (Optimizes Range Queries)

receivedTime

Timestamp

Server Receipt Time

Used for Conflict Resolution

duration

Long

Duration (ms)

Metric

defectCount

Integer

Count

Metric

5. 🚀 Performance Strategy

Goal: Process 1,000 events in < 1 second.
Result: ~120ms (Avg).

Key Optimizations

Eliminating the N+1 Problem:

Naive: Loop 1000 times $\to$ 1000 Select queries.

Optimized: 1 Select (fetch all) + 1 Save (persist all). Reduced DB calls by 99%.

Batch Writes:

Leveraged JPA saveAll() with Hibernate batching to group SQL statements into single network packets.

Database-Side Aggregation:

Stats (Sum, Count) are calculated via native SQL, keeping memory footprint low.

6. ✅ Tested Scenarios & Edge Cases

The system includes a mandatory JUnit test suite covering all 8 requirements:

[Test 1] Identical Duplicates: Silently ignored; DB count unchanged.

[Test 2] Last-Write-Wins: Newer timestamp overwrites older data.

[Test 3] Out-of-Order Data: Older timestamp is discarded.

[Test 4] Invalid Duration: Events < 0 or > 6hrs are rejected.

[Test 5] Future Timestamps: Events > 15mins in future are rejected.

[Test 6] Unknown Defects (-1): Stored as -1, but counted as 0 in Stats aggregation.

[Test 7] Time Boundaries: Strict inclusive-start / exclusive-end logic.

[Test 8] Concurrency: 20 parallel threads verified for race conditions.

7. ⚙️ Setup & Run Instructions

Prerequisites

Java 17+

Maven

Quick Start

Run the App:

./mvnw spring-boot:run


Run Tests:

./mvnw test


Endpoints

Ingest: POST http://localhost:8080/events/batch

Stats: GET http://localhost:8080/stats?machineId=M-1&start=...&end=...

Top Defects: GET http://localhost:8080/stats/top-defect-lines?from=...&to=...

DB Console: http://localhost:8080/h2-console

8. 🔮 Future Improvements

Asynchronous Processing: Introduce Kafka. The API should acknowledge receipt immediately, while consumers process DB writes in the background to handle bursty traffic.

Persistent Storage: Migrate from H2 to TimescaleDB or PostgreSQL for data durability and time-series optimization.

Caching: Add Redis for the Stats API to cache historical reports.

Validation: Replace manual if checks with Jakarta @BeanValidation annotations.
