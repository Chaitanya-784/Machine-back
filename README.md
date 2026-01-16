
# Factory Event Ingestion System

> A high-performance, thread-safe backend service designed to ingest machine sensor events from factory equipment and provide real-time statistical reporting.

**Tech Stack:** Java 17 | Spring Boot 3 | H2 Database | Spring Data JPA

---

## 🚀 Quick Start & Run Instructions

Follow these steps to get the application running locally.

### Prerequisites
* **Java 17** or higher installed.
* **Maven** installed (or use the provided `mvnw` wrapper).

### Running the Application
1.  Navigate to the project root directory.
2.  Run the application using the Maven wrapper:
    ```bash
    ./mvnw spring-boot:run
    ```
3.  The application will start on **port 8080**.

### Running Tests
To execute the comprehensive test suite (including concurrency and edge-case validation):
```bash
./mvnw test

```

### Accessing the System

Once the server is running, you can access the following endpoints:

| Feature | Method | URL |
| --- | --- | --- |
| **Batch Ingestion** | `POST` | `http://localhost:8080/events/batch` |
| **Get Stats** | `GET` | `http://localhost:8080/stats?machineId=...&start=...&end=...` |
| **Top Defects** | `GET` | `http://localhost:8080/stats/top-defect-lines?machineId=...` |
| **H2 Console** | `GUI` | `http://localhost:8080/h2-console` |

---

## 🏗 Architecture

The system follows a **Modular Monolithic** architecture designed for simplicity, maintainability, and speed.

### 1. API Layer (`EventController`)

* **Role:** Entry point for HTTP requests.
* **Responsibilities:** Handles request mapping, validates JSON input structure/types, and delegates processing to the Service layer. Ensures strictly typed responses.

### 2. Service Layer (`EventService`)

* **Role:** Core business logic.
* **Responsibilities:**
* **Validation:** Enforces business rules (duration limits, timestamp sanity).
* **Deduplication:** Identifies previously processed events.
* **Conflict Resolution:** Implements "Last-Write-Wins" strategy.
* **Batch Optimization:** Orchestrates bulk processing to minimize DB interaction.



### 3. Data Layer (`MachineEventRepository`)

* **Role:** Persistence management using Spring Data JPA & H2.
* **Decision:** **H2 In-Memory Database** was chosen to allow local execution without installation while maintaining ACID compliance and SQL support.
* **Optimization:** Uses custom JPQL queries for statistical aggregations (grouping, summing) to offload heavy lifting to the database engine.

---

## ⚡ Deduplication & Update Logic

To handle high-frequency sensor networks where duplicates and out-of-order delivery are common, the system uses a **"Bulk Read-Modify-Write"** strategy.

### The Strategy

Instead of processing events one by one (causing N+1 issues), the system processes incoming batches in three phases:

1. **Bulk Fetch:** Extracts unique IDs and retrieves existing DB records in a single query.
2. **In-Memory Comparison:** Iterates through the batch and compares against existing records.
3. **Batch Write:** Persists changes in a single transaction.

### Logic Matrix

| Scenario | Condition | Action | Logic Applied |
| --- | --- | --- | --- |
| **Duplicate** | ID exists + Payload Identical | **Ignore** | Deduplication |
| **Newer Data** | ID exists + Payload differs + Incoming time is *Newer* | **Update** | Last-Write-Wins |
| **Older Data** | ID exists + Payload differs + Incoming time is *Older* | **Ignore** | Obsolete Data Discard |
| **New Event** | ID does not exist | **Insert** | New Record Creation |

> **Race Condition Handling:** The system maintains a real-time local map during batch processing. If a single batch contains multiple updates for the same ID, subsequent events in that batch compare themselves against the *most recently updated* local state, preventing duplicate key errors.

---

## 🔒 Thread Safety

The system is designed to handle concurrent requests from multiple sensors safely through three layers of defense:

1. **Database Transactions (`@Transactional`)**
* The entire batch process runs in a single transaction with **Read Committed** isolation.
* Ensures atomicity: either the whole batch succeeds, or it rolls back.


2. **Unique Constraints**
* The database schema enforces a `UNIQUE` constraint on the `eventId` column.
* **Safety Net:** Even if application logic fails, the DB rejects duplicate insertions, ensuring strict data consistency.


3. **Stateless Design**
* Service and Controller layers are stateless singletons.
* No mutable state is shared between threads; each request has its own isolated memory context.



---

## 📊 Data Model & Performance

### Schema: `machine_events`

* **Event ID (PK):** String-based unique identifier.
* **Timestamps:**
* `eventTime`: When the sensor recorded data (used for reporting).
* `receivedTime`: When the system received data (used for conflict resolution).


* **Metrics:** `duration` (ms) and `defect_count` (int).

### Performance Strategies

The system achieves the target of processing **1,000 events in < 1 second** via:

* **Eliminating N+1 Problems:** Reduces DB calls from  (batch size) to 2 (1 Select + 1 Save) per batch.
* **Batch Writes:** Leverages JPA `saveAll()` and Hibernate optimizations to group Insert/Update statements into single network packets.
* **Database-Side Aggregation:** Uses SQL `SUM()` and `COUNT()` for reports, avoiding loading thousands of rows into Java memory.
* **Indexing:**
* **PK Index:** For  lookups during ingestion.
* **Composite Index (`machineId` + `eventTime`):** Optimizes Statistics API queries by locating relevant time windows instantly.



---

## 🧪 Tested Scenarios (Edge Cases)

The system is verified by a comprehensive `EventServiceTest` suite covering these mandatory scenarios:

| # | Scenario | Handling Logic | Result |
| --- | --- | --- | --- |
| **1** | **Identical Duplicate** | Detected via `hasSamePayload()`. | Silently ignored; DB row count unchanged. |
| **2** | **Newer Data Update** | Timestamp conflict detected; incoming is newer. | **Update** performed; fields overwritten. |
| **3** | **Out-of-Order Data** | Timestamp conflict detected; incoming is older. | **Ignored**; DB state remains unchanged. |
| **4** | **Invalid Duration** | Duration < 0 or > 6 hours. | Rejected by validation logic. |
| **5** | **Future Timestamp** | Event time > 15 mins in future. | Rejected to prevent clock sync corruption. |
| **6** | **Unknown Defect (-1)** | Sensor error sending `-1`. | Stored as raw data, but treated as `0` in Sum calculations. |
| **7** | **Time Window** | Querying specific start/end times. | Standard inclusive-start / exclusive-end logic applied. |
| **8** | **Concurrency** | Multiple threads inserting unique events. | ACID transactions ensure final count is exact; no race conditions. |

---

## 🔮 Future Improvements

Given more time, the system would be upgraded for production scale with:

* [ ] **Asynchronous Processing:** Introduce **Apache Kafka** to buffer ingress traffic, allowing the API to acknowledge receipt immediately while consumers process DB writes in the background.
* [ ] **Persistent Storage:** Migrate from H2 to **PostgreSQL/TimescaleDB** for data durability and better compression of historical time-series data.
* [ ] **Caching Strategy:** Implement **Redis** for the Statistics API to cache results of immutable past time windows.
* [ ] **Declarative Validation:** Replace manual checks with Jakarta Bean Validation (`@PastOrPresent`, `@Min`) for cleaner code.

```

```
