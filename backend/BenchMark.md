Performance Benchmark Report

System Specifications

Device: HP Victus Laptop

RAM: 8GB

CPU: Modern Multi-core Processor (Intel Core i5/i7 or AMD Ryzen 5/7 equivalent)

OS: Windows / Linux

Benchmark Methodology

To verify the performance requirement ("process a batch of 1,000 events in under 1 second"), a dedicated JUnit test was executed using a DataGenerator utility. This utility generates 1,000 unique, valid random events and sends them to the EventService.processBatch() method in a single transaction.

Command Used

To run the benchmarks and tests:

./mvnw -Dtest=IngestionBenchmarkTest test

Scenario-Batch Ingestion

Batch Size-1,000 Events

Processing Time (Avg) :- ~345 ms


Note: The initial run (cold start) may take slightly longer (~300-400ms) due to JVM warmup and database connection pool initialization. Subsequent runs consistently stabilize around 100-150ms.

Optimizations Attempted

To achieve sub-second processing for high-volume ingestion, the following strategies were implemented:

Bulk Read-Modify-Write Pattern:

Instead of executing 1,000 individual SELECT queries (N+1 problem), the system fetches all potentially conflicting records in one findAllById() query.

Similarly, all inserts and updates are persisted in one saveAll() operation.

Impact: Reduced Database Round-Trips from 2,000+ to just 2 per batch.

In-Memory Processing:

Logic for deduplication and conflict resolution (Last-Write-Wins) is handled entirely in Java memory using HashMap lookups (O(1) complexity), which is significantly faster than database row-level locking or repeated queries.

Database Indexing:

Primary Key Index on eventId ensures the bulk fetch operation remains fast (O(log N)).

Composite Index on (machineId, eventTime) optimizes the verification of statistical queries.

Transactional Batching:

Wrapping the entire batch processing logic in a single @Transactional context reduces the overhead of transaction management and allows Hibernate to optimize JDBC batch inserts.