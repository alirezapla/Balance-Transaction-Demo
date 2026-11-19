# Concurrency, Transactions and Distributed Systems — Interview Scenarios

This document turns the repository into a practical Senior Java interview lab. Each scenario contains a problem statement, the invariant that must remain true, and the concepts an interviewer may expect you to discuss.

## How to use this lab

For each scenario:

1. Explain the failure with a timeline before writing code.
2. State the invariant that must never be violated.
3. Reproduce the bug with a deterministic integration test where possible.
4. Implement the smallest correct solution.
5. Run the test repeatedly and under concurrent load.
6. Explain the trade-offs and what changes with multiple application instances.

Do not treat every scenario as already implemented. The **Current implementation** column distinguishes existing behavior from follow-up exercises.

## Scenario catalogue

| # | Scenario | Failure / question | Invariant or expected result | Current implementation |
|---|---|---|---|---|
| 1 | Lost update | Two requests read the same balance and overwrite each other's update. | Both accepted credits are reflected exactly once. | Implemented; row-level pessimistic locking and integration test. |
| 2 | Negative balance / double spending | Two concurrent debits each see enough funds before either commits. | A wallet balance never becomes negative. | Implemented; row lock and concurrent debit test. |
| 3 | Atomic transfer | Source debit succeeds but destination credit fails. | Both sides commit, or neither side commits. | Implemented; one database transaction and rollback test. |
| 4 | Deadlock on opposite transfers | T1 transfers A→B while T2 transfers B→A. | No lock-order cycle; both operations eventually complete or fail within a bounded timeout. | Implemented by locking account IDs in deterministic order; add a stress test. |
| 5 | Duplicate request / idempotency | A client retries after a timeout although the first request may have committed. | One transaction ID and one request fingerprint produce one financial effect. | Implemented with a unique constraint and PostgreSQL conflict handling. |
| 6 | Idempotency-key collision | A client reuses a key with a different amount or operation. | A key cannot silently represent two different commands. | Implemented; returns conflict. |
| 7 | Partial operation failure | An exception occurs after the transaction row or first ledger entry is inserted. | Transaction row, ledger entries and balance projection roll back together. | Implemented and covered by a database-trigger integration test. |
| 8 | Transaction boundary / Spring proxy | A `@Transactional` method calls another transactional method through `this`. | The developer understands proxy-based transaction interception and propagation. | Interview exercise; add a small isolated service/test. |
| 9 | Checked vs unchecked exception rollback | A checked exception is thrown after a write. | Rollback policy is explicit (`rollbackFor`) rather than assumed. | Interview exercise. |
| 10 | `REQUIRED` vs `REQUIRES_NEW` | An inner operation fails while an outer operation continues. | The resulting commit/rollback boundaries match the business requirement. | Interview exercise; demonstrate with an audit/outbox example. |
| 11 | Isolation anomalies | Compare READ COMMITTED, REPEATABLE READ and SERIALIZABLE under concurrent reads/writes. | The chosen isolation level prevents the specific anomaly required by the use case. | Follow-up experiment; PostgreSQL is the target database. |
| 12 | Optimistic vs pessimistic locking | Compare `@Version` with `PESSIMISTIC_WRITE`. | Choose based on contention, retry cost and conflict frequency. | Follow-up branch/exercise; current wallet writes use pessimistic locking. |
| 13 | Lock timeout and transaction timeout | A request waits too long for a hot account row. | Requests fail predictably; locks are released and clients can retry safely. | Follow-up exercise; configure DB/JPA lock timeout and map timeout errors. |
| 14 | Phantom / predicate race | Two requests both check a business condition and insert rows. | A cross-row invariant is protected by a unique/exclusion constraint or suitable isolation/locking. | Interview exercise; use a reservation/limited-capacity example. |
| 15 | Unique constraint as concurrency control | Application performs `SELECT` then `INSERT` for a supposedly unique key. | Correctness is enforced at the database boundary, not by a check-then-act race. | Idempotency uses a unique database constraint. Extend to account creation tests. |
| 16 | Retry after serialization failure | PostgreSQL aborts a transaction due to serialization/deadlock conflict. | Retry the entire transaction with bounded attempts and jitter; never retry only half an operation. | Follow-up exercise. |
| 17 | Retry safety | A client retries a debit after a network timeout. | Idempotency makes retries safe even when the client cannot know whether commit happened. | Implemented for synchronous requests; add fault-injection tests. |
| 18 | Reconciliation snapshot consistency | Ledger rows change while a long reconciliation scans accounts. | Report semantics are explicit: point-in-time snapshot or bounded eventual view. | Follow-up exercise; define isolation/snapshot strategy. |
| 19 | Stale `RUNNING` job recovery | Process crashes after persisting a run as RUNNING. | A stale run is detected and marked/recovered without duplicating unsafe work. | Documented follow-up; not yet implemented. |
| 20 | Distributed scheduler lock | Multiple service instances trigger the same scheduled task. | At most one instance owns the scheduler lock for a lock name at a time. | ShedLock with PostgreSQL. |
| 21 | Outbox pattern | Database commit succeeds but publishing an event to Kafka fails. | A committed business change has a durable event record to publish eventually. | Follow-up exercise; add outbox table, publisher and deduplication. |
| 22 | At-least-once message delivery | Consumer receives the same event more than once. | Consumer side effects are idempotent. | Follow-up exercise; add inbox/processed-message table. |
| 23 | Ordering and aggregate version | Events for one account arrive out of order. | Per-account ordering/version rules prevent stale state transitions. | Follow-up exercise. |
| 24 | Cache consistency | Balance is cached while a transaction commits. | Read-your-writes and invalidation semantics are documented and tested. | Not implemented; add Redis only after defining consistency requirements. |
| 25 | Hot account contention | A popular wallet becomes a serialization point. | Throughput limits and queueing behavior are measured; correctness remains intact. | Follow-up benchmark; discuss partitioning/command queues. |
| 26 | Ledger immutability | Someone updates or deletes a historical posting. | Financial history is append-only; corrections use compensating entries. | Application convention only; DB permissions/triggers are production hardening. |
| 27 | Reversal vs deletion | A settled transaction must be undone. | Original entries remain; a linked compensating transaction restores the balance. | Follow-up exercise. |
| 28 | Money precision | Floating-point arithmetic introduces rounding errors. | Amounts use integer minor units or a rigorously defined decimal representation. | Implemented with positive `long` minor units. |
| 29 | Overflow | A large credit exceeds the range of `long`. | Arithmetic overflow is detected and the transaction is rolled back. | Uses exact arithmetic in service logic; add boundary tests. |
| 30 | Pagination under concurrent writes | Reconciliation history changes while clients page through it. | Stable ordering and cursor semantics avoid duplicates/skips as far as the contract requires. | Current history endpoint is bounded to latest 100; cursor pagination is an exercise. |

## Deep-dive interview prompts

### Locking and concurrency

- Why is `synchronized` insufficient when the application runs on three replicas?
- What SQL does `PESSIMISTIC_WRITE` normally translate to on PostgreSQL?
- Why must two-account transfers acquire locks in a deterministic order?
- Can a row lock protect an invariant spanning multiple rows? When would a database constraint or SERIALIZABLE isolation be preferable?
- What is the difference between lock wait timeout, transaction timeout and query timeout?
- When can optimistic locking outperform pessimistic locking? What does the retry loop look like?
- Why can a global `SYSTEM_CLEARING` lock destroy throughput?

### Transactions and Spring

- What does `@Transactional` actually do in Spring, and why does self-invocation bypass the proxy?
- Which exceptions trigger rollback by default? How do `rollbackFor` and `noRollbackFor` change that?
- Explain `REQUIRED`, `REQUIRES_NEW`, `NESTED` and `SUPPORTS`.
- What happens if a method catches an exception and returns normally after a write?
- Why should remote HTTP/Kafka calls generally not be made while holding a database transaction open?
- How would you test rollback after the first of two ledger inserts?

### Ledger and reliability

- Why is the ledger the source of truth while account balance is a projection?
- How do you prove that every financial transaction balances (sum of debits equals sum of credits)?
- Why should a reversal append new entries instead of modifying old entries?
- What if the database commits but the HTTP response is lost?
- How do idempotency keys behave under two simultaneous requests with the same key?
- How would you make database-to-Kafka publication reliable? Explain the transactional outbox.

### Operations and scale

- How do you recover a scheduler run left in RUNNING after a process crash?
- Should reconciliation run in one giant transaction? Explain snapshot consistency, lock duration and memory pressure.
- How would you monitor lock contention, deadlocks, transaction duration and reconciliation drift?
- What changes when one account receives thousands of writes per second?
- When is sharding by account ID useful, and what does it make harder for cross-account transfers?

## Suggested implementation roadmap

### Level 1 — Database correctness

1. Add a test proving two simultaneous requests with the same idempotency key produce one ledger transaction.
2. Add opposite-direction transfer stress tests to exercise lock ordering.
3. Add lock-timeout handling and a bounded retry policy for deadlock/serialization failures.
4. Add overflow and same-account-transfer boundary tests.

### Level 2 — Spring transaction mechanics

1. Demonstrate self-invocation and proxy boundaries.
2. Compare REQUIRED and REQUIRES_NEW using an audit record.
3. Compare rollback behavior for checked and unchecked exceptions.
4. Compare optimistic and pessimistic locking with a contention benchmark.

### Level 3 — Distributed reliability

1. Implement a transactional outbox and an idempotent publisher.
2. Add an inbox/processed-event table for consumers.
3. Add stale reconciliation run detection and recovery.
4. Add reversal transactions and immutable ledger enforcement at database level.

### Level 4 — Performance and production readiness

1. Add metrics for lock wait, transaction duration, failed operations and reconciliation drift.
2. Add cursor pagination to reconciliation history.
3. Benchmark hot-account contention and independent-account throughput.
4. Add multi-currency support with currency-specific ledger balancing.
5. Define account ownership, authorization, audit retention and operational alerting.

## Test design principles

- Prefer PostgreSQL integration tests for lock, isolation and constraint behavior. H2 is not a substitute for PostgreSQL concurrency semantics.
- Use barriers/latches to align concurrent workers; avoid tests that rely only on `Thread.sleep`.
- Assert both the final projection and the ledger entries. A correct balance with an incorrect ledger is still a financial correctness failure.
- For rollback tests, verify every affected table, not only the HTTP response.
- Run stress tests repeatedly and keep deterministic correctness tests separate from throughput benchmarks.
