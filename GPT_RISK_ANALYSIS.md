# GPT Risk Analysis

## Key Technical Risks

1) **Header-based auth is trivially spoofable**
- Risk: Any client can set `X-Admin-Id`/`X-Admin-Role` to escalate privileges; no signing/expiration.
- Mitigation: Introduce JWT/OAuth2 with signature and expiry; validate against user store; require TLS; log and rate-limit admin endpoints.

2) **Collection pipeline ties up threads for 5 minutes**
- Risk: `CollectorService.waitForCollection()` blocks pool threads for `COLLECTION_DELAY_MILLIS`, limiting throughput and causing timeouts in tests.
- Mitigation: Replace sleep with scheduled/async step (e.g., dispatch job id to scheduler or use `CompletableFuture` with delayed execution); make delay configurable and near-zero under `test` profile; add backpressure/queue metrics.

3) **Request gating can leave stale jobs**
- Risk: `collectionRequestedAt` is set on request and only cleared on start/complete/reset; if poller misses or crashes, requests remain indefinitely and never re-run.
- Mitigation: Add TTL-based cleanup (e.g., reset to `NOT_REQUESTED` after N minutes) or a `REQUESTED` state with retry/backoff; surface pending count via metrics.

4) **Permission gap on collection requests**
- Risk: Any authenticated manager can request collection for any business; VAT reads enforce permissions but writes do not.
- Mitigation: Apply the same permission check in `CollectionService.requestCollection` using `BusinessPlaceAdminRepository` or shared auth helper; add tests.

5) **Polling architecture scales poorly**
- Risk: `ScheduledCollectionPoller` scans all `NOT_REQUESTED`+requested rows every 10s; with many businesses this causes load and latency; no sharding/backoff.
- Mitigation: Move to event/queue (Kafka/RabbitMQ) or add paginated polling with limits and randomized jitter; record last polled id/timestamp to avoid full scans.

6) **Excel ingestion is fragile and silent**
- Risk: Missing file returns empty transactions; row parse errors are only logged; invalid input can silently mark collections as completed with no data.
- Mitigation: Fail fast on missing/unreadable files; accumulate row errors and return failure status; add validation layer and dead-letter/alerting for bad files.

7) **In-memory pagination for VAT**
- Risk: `PageableHelper` slices lists in memory; large business lists will load all ids and sums, causing memory/latency issues.
- Mitigation: Push pagination to the database (repository-level queries with LIMIT/OFFSET) and stream results; keep in-memory only for small lists in tests.

8) **AUTO_SERVER H2 shared file DB**
- Risk: File-based H2 in AUTO_SERVER across API and collector can corrupt under crashes and is not production-ready; no migrations.
- Mitigation: Use a real RDBMS for multi-node; add Flyway/Liquibase migrations; reserve H2 for tests only.

9) **Lack of observability on collection outcomes**
- Risk: No metrics/logs for queue depth, duration, failures; ops cannot detect stuck jobs.
- Mitigation: Emit Micrometer metrics (requested, started, completed, failed, duration), structured logs with job id/businessNumber, and alerts on error rates.

10) **Concurrency gaps in completion path**
- Risk: `complete` fetches business place without lock; another request could slip in between start/complete if the flag is stale.
- Mitigation: Use the locked finder in `complete` or ensure state machine checks collectionRequestedAt/state consistency; consider optimistic version checks.

11) **Test fragility/timeouts**
- Risk: Collector tests depend on real sleep unless mocked; `:collector:test` can hang/timeout in CI.
- Mitigation: Provide test profile overriding delay to milliseconds; inject sleeper/clock to mock in unit tests; add deterministic integration tests.

12) **Error handling on repeated failures**
- Risk: Poller retries endlessly without backoff; failures leave status `NOT_REQUESTED` and immediately retrigger.
- Mitigation: Add retry/backoff policy and failure counters; move failed jobs to `FAILED` with manual retry, or exponential backoff per business.

## High-Value Refactor Targets

- **Collection orchestration**: Replace sleep-based async with a stateful job queue; make request/lock/complete fully idempotent and permission-aware; add TTL/backoff.
- **Pagination and data access**: Move VAT pagination and sums into repository-level queries; remove in-memory slicing for production paths.
- **Auth/security**: Swap header spoofing for JWT/OAuth2; centralize auth/permission checks for both read (VAT) and write (collection) paths.
- **Observability**: Add Micrometer/Actuator metrics and structured logging around collection lifecycle and Excel parsing; alert on failures/stalls.
- **Input validation**: Fail fast on missing/invalid Excel files; surface errors to callers instead of silent empty collections.
