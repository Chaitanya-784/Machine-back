Factory Event Ingestion System

A high-performance, thread-safe backend service designed to ingest machine sensor events from factory equipment and provide real-time statistical reporting. Built with Java 17, Spring Boot 3, and H2 Database.

1. Architecture

The system follows a modular Monolithic architecture designed for simplicity, maintainability, and speed. The application is structured into three distinct layers:

1.1 API Layer (EventController)

The REST controller acts as the entry point for the system. It handles HTTP requests, validates input formats (JSON structure, basic types), and delegates complex processing to the Service layer. It exposes endpoints for batch ingestion (POST) and statistical reporting (GET), ensuring strictly typed responses.

1.2 Service Layer (EventService)

This is the core business logic layer. It is responsible for:

Validation: Enforcing business rules such as duration limits (0-6 hours) and timestamp sanity checks (rejecting future events).

Deduplication: Identifying if an event has already been processed.

Conflict Resolution: implementing the "Last-Write-Wins" strategy based on sensor timestamps.

Batch Optimization: Orchestrating the bulk processing flow to minimize database interactions.

1.3 Data Layer (MachineEventRepository)

The data layer is built on Spring Data JPA backed by an H2 In-Memory Database.

Decision: H2 was selected to meet the requirement of "running locally without installation" while still providing full SQL support, ACID transactions, and index optimization capabilities.

Function: It manages all database interactions, utilizing custom JPQL queries for complex statistical aggregations (like grouping by machine or summing defects) to keep performance high.

2. Deduplication & Update Logic

In high-frequency sensor networks, duplicate data and out-of-order delivery are common. To handle this efficiently without slowing down ingestion, the system uses a "Bulk Read-Modify-Write" strategy.

The Strategy

Instead of processing events one by one (which causes valid performance issues), the system processes the entire incoming batch in three phases:

Bulk Fetch: The system extracts all unique Event IDs from the incoming batch and queries the database once to retrieve any existing records. This reduces database round-trips from N (batch size) to 1.

In-Memory Comparison: The system iterates through the incoming events and compares them against the existing records loaded in memory.

Conflict Resolution Rules:

Dedupe (Ignore): If the Event ID exists and the payload (machine, duration, defect count) is identical, the incoming event is discarded as a duplicate.

Update: If the Event ID exists but the payload differs, the system compares the receivedTime.

If the incoming event is newer, it updates the existing record (Last-Write-Wins).

If the incoming event is older, it is ignored (obsolete data).

Insert: If the Event ID does not exist in the database, it is marked for insertion.

Race Condition Handling (Intra-Batch)

A critical edge case occurs if a single batch contains multiple updates for the same Event ID. To prevent logic errors, the system maintains a real-time local map during processing. As soon as an event is accepted or updated, this local map is updated. This ensures that subsequent events in the same batch compare themselves against the most up-to-date state, preventing duplicate key errors.

3. Thread Safety

The system is designed to handle concurrent requests from multiple sensors safely. Thread safety is enforced at multiple levels:

Database Transactions

The entire batch processing method runs within a single Database Transaction (@Transactional). This ensures atomicity: either the entire batch logic completes successfully, or it rolls back. The database isolation level (Read Committed) prevents dirty reads between concurrent threads.

Unique Constraints

The database schema strictly enforces a UNIQUE constraint on the eventId column. This acts as the final safety net. Even if two concurrent threads somehow bypass the application-level deduplication logic, the database will reject the second insertion attempt with a constraint violation, ensuring data consistency is never compromised.

Stateless Design

The Service and Controller layers are stateless singletons. No mutable state is shared between request threads in Java memory. Each request has its own isolated context (variables, maps, lists), preventing memory corruption issues common in multi-threaded Java applications.

4. Data Model

The data is stored in a single, optimized table named machine_events.

Schema Overview

Event ID (Primary Key): A string-based unique identifier. This is indexed for O(1) lookups during the deduplication phase.

Machine ID: Identifies the source machine.

Timestamps: Two timestamps are stored:

Event Time: When the sensor recorded the data (used for reporting).

Received Time: When the system received the data (used for conflict resolution).

Metrics: Stores duration (milliseconds) and defect_count (integer).

Indexing Strategy

To ensure the system meets performance targets, specific database indexes are applied:

Primary Key Index: Automatically created on Event ID to speed up the ingestion checks.

Composite Index (machineId + eventTime): A custom composite index is created to optimize the Statistics API. Since queries always filter by a specific machine and a time range, this index allows the database to locate the relevant rows instantly without scanning the entire table.

5. Performance Strategy

The primary requirement was to process 1,000 events in under 1 second. The following optimizations were implemented to achieve this:

Eliminating the N+1 Problem

A naive implementation would loop through 1,000 events and execute a SELECT query for each one to check existence. This would result in 1,000 database calls per batch, causing massive latency.

Optimization: The system performs one SELECT query to fetch all relevant existing records and one SAVE query to persist changes. This reduces database interaction overhead by approximately 99%.

Batch Writes

The system leverages JPA's saveAll() functionality. Combined with Hibernate configuration, this allows the database driver to group multiple Insert/Update statements into a single network packet sent to the database, drastically improving write throughput.

Database-Side Aggregation

For the statistics endpoints, calculations (Sum, Count) are performed directly within the database engine using SQL SUM() and COUNT() functions. This avoids fetching thousands of rows into Java memory just to calculate a total, keeping the memory footprint low and response times fast.

6. Tested Scenarios & Edge Cases

The system includes a comprehensive JUnit test suite (EventServiceTest) that explicitly verifies the 8 mandatory scenarios outlined in the requirements.

1. Identical Duplicate Handling

Scenario: Receiving the exact same eventId and payload twice.

Handling: The system detects the payload match using hasSamePayload() and silently ignores the duplicate.

Result: deduped count increments; database row count does not increase.

2. Update via "Last-Write-Wins" (Newer Data)

Scenario: Receiving an existing eventId with a different payload and a newer receivedTime.

Handling: The system identifies the timestamp conflict, trusts the newer timestamp, and performs an update (not an insert).

Result: updated count increments; database fields are overwritten with new values.

3. Out-of-Order Data (Older Data)

Scenario: Receiving an existing eventId with a different payload but an older receivedTime.

Handling: The system assumes the current database state is more recent and discards the incoming event.

Result: deduped (or ignored) count increments; database state remains unchanged.

4. Invalid Duration Validation

Scenario: Events with durationMs < 0 or > 6 hours (21,600,000 ms).

Handling: Explicit validation check in processBatch rejects these specific items before database processing.

Result: rejected count increments; event is not stored.

5. Future Timestamp Validation

Scenario: Events with eventTime > 15 minutes into the future (relative to server time).

Handling: Validation logic checks Instant.now().plus(15, ChronoUnit.MINUTES) and rejects violations.

Result: rejected count increments; prevents data corruption from synchronized clocks.

6. "Unknown" Defect Handling (-1)

Scenario: Events where defectCount is -1 (sensor error).

Handling: The event is stored with -1 to preserve raw data. However, the Stats API Query uses SQL SUM(CASE WHEN defectCount = -1 THEN 0 ELSE defectCount END).

Result: The event contributes to the total "Event Count" but adds 0 to the "Defect Count" in statistical reports.

7. Time Window Boundaries

Scenario: Querying stats for a specific time window (e.g., 10:00 to 11:00).

Handling: The JPQL query enforces standard inclusive-start/exclusive-end logic: eventTime >= start AND eventTime < end.

Result: An event exactly at 10:00:00 is included; an event exactly at 11:00:00 is excluded (belongs to next window).

8. Concurrency & Thread Safety

Scenario: Multiple threads processing batches simultaneously (simulated with ExecutorService).

Handling: Relies on Spring @Transactional and database ACID properties.

Result: Even with 20 parallel threads inserting unique events, the final count matches exactly, confirming no race conditions or lost updates.

7. Setup & Run Instructions

Prerequisites

Java 17 or higher installed.

Maven installed (or use the provided wrapper).

Running the Application

Navigate to the project root directory.

Run the application using the Maven wrapper:
./mvnw spring-boot:run

The application will start on port 8080.

Running Tests

To execute the mandatory test suite (including concurrency and edge case tests):
./mvnw test

Accessing the System

Batch Ingest (POST): http://localhost:8080/events/batch

Get Stats (GET): http://localhost:8080/stats?machineId=...&start=...&end=...

Get Defect : http://localhost:8080/stats/top-defect-lines?machineId=&from=...&to=...&limit=

H2 Database Console: http://localhost:8080/h2-console

8. Future Improvements

Given more time, the system would be enhanced with the following production-grade features:

Asynchronous Processing: Introduce a message queue (like Apache Kafka) between the API and the Service. The API would acknowledge receipt immediately, and consumers would process database writes in the background. This would allow the system to handle massive, bursty traffic loads without timeouts.

Persistent Storage: Migrate from H2 (In-Memory) to a time-series optimized database like TimescaleDB or PostgreSQL. This would provide data durability across restarts and better compression for historical data.

Caching Strategy: Implement Redis caching for the Statistics API. Since historical data for past time windows is immutable, caching the results would significantly reduce database load for frequently accessed reports.

Declarative Validation: Replace manual validation logic with Jakarta Bean Validation annotations (e.g., @PastOrPresent, @Min) to make the code cleaner and more standardized.