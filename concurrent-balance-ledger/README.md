# Concurrent Balance Service — Java Concurrency & Transaction Interview Lab

A hands-on Senior Java interview preparation project built with **Java 21, Spring Boot 3.5, Spring Data JPA and PostgreSQL**.

The repository is intentionally centered on correctness under concurrent requests. It demonstrates database transactions, row-level locking, idempotency, double-entry bookkeeping, rollback behavior and reconciliation. It also provides a growing catalogue of interview scenarios that can be implemented as exercises.

> This is an educational service, not a production-ready banking system. Authentication, authorization, currency support, external settlement, operational alerting and a formal ledger immutability policy still require production design.

## Learning objectives

By working through this repository, you should be able to explain and demonstrate:

- Lost updates, dirty reads, non-repeatable reads and phantom reads.
- Pessimistic vs optimistic locking and database constraints as concurrency controls.
- Deadlocks, deterministic lock ordering, lock timeouts and retries.
- Spring `@Transactional`, proxy behavior, propagation and rollback rules.
- Atomic multi-row operations and failure rollback.
- Idempotency under retries and simultaneous duplicate requests.
- Double-entry ledger design and balance projections.
- Reconciliation, scheduler coordination and distributed reliability patterns.
- Transactional outbox, inbox deduplication and at-least-once delivery (planned exercises).

## Technology stack

| Technology | Purpose |
|---|---|
| Java 21 | Application runtime |
| Spring Boot 3.5 | Application framework and REST API |
| Spring Web | HTTP endpoints and `ResponseEntity` |
| Spring Data JPA / Hibernate | Entity mapping, repositories and row locks |
| PostgreSQL 16 | Transactional persistence and concurrency control |
| ShedLock | Distributed lock for scheduled reconciliation |
| Testcontainers | PostgreSQL-backed integration tests |
| Maven | Build and dependency management |
| Postman | Manual API workflow and response assertions |

## Architecture

```text
HTTP Client / Postman
        |
        v
BalanceController ---- ReconciliationController endpoints
        |                         |
        v                         v
BalanceService              ReconciliationRunner
        |                         |
        v                         v
Spring Data Repositories    ReconciliationHistoryService
        |                         |
        +------------+------------+
                     |
                     v
                 PostgreSQL
      +--------------+----------------+
      | Account / FinancialTransaction|
      | LedgerEntry / Reconciliation  |
      | ShedLock                      |
      +-------------------------------+
```

### Core domain model

- **Account** stores the wallet's current balance projection and account metadata.
- **FinancialTransaction** stores the idempotency key, operation type and request fingerprint.
- **LedgerEntry** stores debit/credit postings linked to a financial transaction and account.
- **ReconciliationRun** stores each operational reconciliation execution.
- **ReconciliationDifference** stores account-level differences found by a run.

The ledger is the accounting record; the account balance is a materialized projection optimized for reads. The service updates both in the same database transaction.

### Double-entry posting rules

Wallet accounts have a CREDIT normal balance. The technical `SYSTEM_CLEARING` counter-account has a DEBIT normal balance.

| Business operation | Debit entry | Credit entry |
|---|---|---|
| Credit wallet | `SYSTEM_CLEARING` | Wallet |
| Debit wallet | Wallet | `SYSTEM_CLEARING` |
| Transfer | Source wallet | Destination wallet |

Amounts are positive integer minor units (for example, cents). Floating-point types must not be used for money.

## Concurrency design

### Pessimistic row locking

Wallet-changing operations lock the affected wallet row using JPA `PESSIMISTIC_WRITE`, which PostgreSQL implements with row-level locking. A concurrent debit must wait for the first transaction to commit or roll back, then re-evaluate the available balance.

For a transfer, both wallet rows are locked in a deterministic order (lexicographic account ID). This avoids the classic cycle where one transaction locks A then waits for B while another locks B then waits for A.

### Why not `synchronized` or `ReentrantLock`?

An in-memory lock coordinates threads only inside one JVM. It cannot coordinate two or more application replicas. The database is the correctness boundary because every instance shares the same transactional database.

A JVM-local lock can sometimes be an optimization, but it must never replace the database constraint/lock that protects financial invariants.

### Idempotency

A unique database constraint protects `transaction_id`. PostgreSQL's atomic conflict handling ensures concurrent requests using the same key cannot both create independent financial transactions. The service compares a request fingerprint:

- Same key + same payload: return success without applying the operation again.
- Same key + different payload: reject with HTTP 409 Conflict.

The database constraint is essential. A `SELECT` followed by an `INSERT` without a unique constraint is vulnerable to a check-then-act race.

### Atomicity

A financial operation writes its transaction record, ledger entries and wallet balance projection inside one database transaction. If any required write fails, the whole operation rolls back. A transfer must never leave only the source debit or only the destination credit committed.

## Reconciliation

The service compares each wallet's stored balance with its ledger-derived balance. Reconciliation reports discrepancies; it does not silently rewrite balances. Corrections should be represented by auditable adjustment or reversal transactions.

The scheduled reconciliation job runs daily at 02:00 UTC by default. ShedLock uses PostgreSQL to prevent multiple application instances from running the same scheduled task simultaneously. The lock is distributed; it is not a JVM-local mutex.

Each run is persisted with a status (`RUNNING`, `SUCCESS`, `INCONSISTENT` or `FAILED`), timestamps and account counts. Account-level differences are stored separately for historical reporting. The run is split into short transactions so a failure during the calculation does not erase the initial RUNNING history record.

## REST API

Base URL: `http://localhost:8080`

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/accounts` | Create a wallet and its opening ledger transaction |
| `GET` | `/api/accounts/{id}/balance` | Read current wallet balance |
| `POST` | `/api/transactions/credit` | Credit a wallet |
| `POST` | `/api/transactions/debit` | Debit a wallet |
| `POST` | `/api/transactions/transfer` | Transfer between wallets atomically |
| `GET` | `/api/reconciliation` | Calculate a non-persisted reconciliation report |
| `POST` | `/api/reconciliation/runs` | Run reconciliation and persist its report |
| `GET` | `/api/reconciliation/runs` | Get recent reconciliation runs |
| `GET` | `/api/reconciliation/runs/{id}` | Get one run and its differences |

### Example requests

Create an account:

```http
POST /api/accounts
Content-Type: application/json
```

```json
{
  "accountId": "WALLET-100",
  "initialBalance": 100000
}
```

Credit:

```http
POST /api/transactions/credit
Content-Type: application/json
```

```json
{
  "accountId": "WALLET-100",
  "amount": 2500,
  "transactionId": "CREDIT-2026-0001"
}
```

Transfer:

```http
POST /api/transactions/transfer
Content-Type: application/json
```

```json
{
  "sourceAccountId": "WALLET-100",
  "destinationAccountId": "WALLET-200",
  "amount": 500,
  "transactionId": "TRANSFER-2026-0001"
}
```

Amounts are integer minor units. The API currently omits currency intentionally.

### HTTP status conventions

- `201 Created`: account created.
- `200 OK`: successful read or financial operation.
- `400 Bad Request`: malformed request or validation failure.
- `404 Not Found`: account or reconciliation run does not exist.
- `409 Conflict`: insufficient funds, idempotency conflict or uniqueness conflict.
- `500 Internal Server Error`: unexpected server failure.

Error responses contain `timestamp`, `status`, `error`, `message` and `path`.

## Run locally

Requirements: Java 21, Maven 3.9+ and Docker with Docker Compose.

Start PostgreSQL:

```bash
docker compose up -d
```

Start the application:

```bash
mvn spring-boot:run
```

The API is available at `http://localhost:8080`.

To reset the local database and start with a clean state:

```bash
docker compose down -v
docker compose up -d
```

Then restart the application. **The reset command deletes local database data.**

## Postman collection

Import these files into Postman:

- `postman/Concurrent-Balance-Service.postman_collection.json`
- `postman/Local.postman_environment.json`

Select the **Concurrent Balance Service - Local** environment and run the collection in order. It creates two wallets and exercises balances, credit, idempotent retry, conflicting idempotency payload, debit, transfer, overdraft rejection, validation, unknown-account handling and reconciliation history.

The collection uses fixed account and transaction IDs. If you run it again against a database that already contains those accounts, reset the local database first or change the environment IDs. The Postman collection is a sequential API smoke test; it does not create true simultaneous requests and is not a replacement for concurrency integration tests.

Optional command-line execution with Newman:

```bash
npm install --global newman
newman run postman/Concurrent-Balance-Service.postman_collection.json \
  -e postman/Local.postman_environment.json
```

## Automated tests

Run the test suite:

```bash
mvn test
```

Docker must be available because the integration tests use PostgreSQL Testcontainers. The tests cover:

1. **BalanceNegative** — debit greater than the available balance is rejected; no ledger entry is written.
2. **UpdateLost** — concurrent credits to one account are serialized; no accepted update is lost.
3. **BalanceIncorrect** — reconciliation detects a deliberately corrupted balance projection.
4. **PartialOperationRollback** — a database trigger fails the second transfer posting; the transaction row, first posting and both projections roll back.
5. Idempotency and concurrent debit/overdraft protection.
6. Reconciliation run history and discrepancy persistence.

PostgreSQL integration tests are intentional. H2 does not reproduce all PostgreSQL locking, isolation and constraint behavior and should not be treated as an equivalent concurrency test environment.

## Interview scenario roadmap

See [`docs/INTERVIEW_SCENARIOS.md`](docs/INTERVIEW_SCENARIOS.md) for a structured practice catalogue. It includes lost updates, double spending, deadlocks, isolation anomalies, Spring transaction proxy behavior, rollback rules, optimistic vs pessimistic locking, lock timeouts, outbox/inbox, stale job recovery, hot-account contention, ledger reversals and more.

The catalogue labels scenarios as implemented or follow-up exercises. Do not assume every listed pattern is already implemented in the codebase.

## Current limitations and production hardening

- `SYSTEM_CLEARING` is a simplified counter-account, not an external settlement integration.
- Ledger immutability is an application convention. Production should prohibit ledger UPDATE/DELETE using database roles and/or triggers.
- Currency, authenticated ownership, authorization and account-level access control are omitted.
- Reconciliation does not automatically repair differences.
- Stale `RUNNING` reconciliation recovery and alerting on `INCONSISTENT`/`FAILED` runs remain follow-up work.
- Add reversal/adjustment workflows, metrics, tracing, pagination, retention policies and operational runbooks before production use.
- Define reconciliation snapshot semantics for large datasets and concurrent writes.

## Suggested study order

1. Run the API collection and understand the ledger entries created by each operation.
2. Read `BalanceServiceImpl` and identify every transaction boundary and lock acquisition.
3. Run the integration tests and explain why each would fail without its protection.
4. Implement the Level 1 exercises in `docs/INTERVIEW_SCENARIOS.md`.
5. Move to Spring transaction mechanics, then distributed reliability and performance.
