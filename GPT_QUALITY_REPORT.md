# Project Quality Report

## Snapshot
- Architecture: Kotlin/Spring Boot multi-module (api-server, collector, infrastructure, common). Clear separation of API vs collector, domain helpers, and tests per module.
- Domain rules: CollectionStatus transitions encapsulated in entities; request gating added via `collectionRequestedAt`; collector uses pessimistic lock on start.
- Testing: Good unit and integration coverage in api-server/infrastructure; collector tests require delay mocking to run within time limits.

## Strengths
- Modular boundaries with shared domain utilities and helpers reduce coupling.
- JPA usage is type-safe (DTO projections) and encapsulates state transitions.
- Input validation on DTOs and path traversal safeguards in Excel parser.

## Quality Gaps (brief)
- Auth spoofable: header-based `X-Admin-Id/Role` without signing.
- Collection flow blocks threads for 5 minutes; poller scans all pending jobs; stale requests not auto-cleaned.
- Permission asymmetry: VAT read checks exist; collection request lacks per-business permission check.
- In-memory VAT pagination; H2 AUTO_SERVER used across apps; limited observability/metrics.

## Refactoring Pointers (short)
1) Replace header auth with JWT/OAuth2; centralize permission checks for collection and VAT.
2) Make collection delay configurable; remove `Thread.sleep` via scheduled jobs/queue; add TTL/backoff for requested jobs.
3) Move VAT pagination and sums into repository-level paged queries; limit in-memory slicing to tests.
4) Add Micrometer/Actuator metrics for collection lifecycle and Excel ingestion; fail fast on missing/invalid files.
5) Swap shared H2 for a real RDBMS in non-test profiles; add Flyway/Liquibase migrations.

## Test/Build Notes
- Recent targeted suites passed: `:api-server` (BusinessPlaceServiceTest, VatCalculationServiceTest, BusinessPlaceControllerTest) and `:infrastructure:test`.
- `:collector:test` times out here due to 5-minute simulated delay—rerun with a reduced delay or mocked sleeper to validate.
