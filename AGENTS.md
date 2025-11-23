# Repository Guidelines

## Project Structure & Module Organization
- Multi-module Gradle (Kotlin DSL) targeting JDK 21.
- `common/`: framework-free enums/exceptions.
- `infrastructure/`: JPA entities/repos/utils; library jar (no bootJar).
- `api-server/`: REST controllers/services/dtos/security; resources under `api-server/src/main/resources`; collection requests now set `collectionRequestedAt`.
- `collector/`: async collector, scheduler, excel parsing; `collector/src/main/resources/application.yml` controls `collector.data-file` (defaults to `sample.xlsx`); poller consumes only `NOT_REQUESTED` rows with a request timestamp.
- `docs/`, `project.md`, `SCHEMA.md` hold domain/spec notes; `sample.xlsx` is the demo data file.
- Tests live in each module’s `src/test/kotlin` (see `api-server/.../TaxApiIntegrationTest.kt` for full stack coverage).

## Build, Test, and Development Commands
- `./gradlew clean build` — full build with all module tests.
- `./gradlew test` — quick test sweep across modules.
- `./gradlew :api-server:bootRun` — run API on 8080 (file-based H2 at `~/tax-data/taxdb`).
- `./gradlew :collector:bootRun` — run collector on 8081; keep API running for end-to-end flows.
- `./gradlew :api-server:test --tests "*ControllerTest"` — example targeted test run.
- Prefer `clean build` (not `-x test`) before pushing.
- Collector tests simulate a 5-minute delay; mock `Thread.sleep` or override the delay for fast local/CI runs.

## Coding Style & Naming Conventions
- Kotlin 1.9 / Spring Boot 3.5 with 4-space indent, trailing newline, no tabs.
- Package prefix `com.kcd.tax`; classes in PascalCase, functions/vars in lowerCamelCase, constants in UPPER_SNAKE_CASE.
- Favor `val`, constructor injection, and null-safety; keep domain logic in `common`/`infrastructure`, thin controllers in `api-server`.
- DTOs live in `api-server/src/main/kotlin/.../dto`; reuse enums from `common` for statuses/roles.

## Testing Guidelines
- JUnit 5 (`useJUnitPlatform`), MockK (plus SpringMockK), and Spring Boot Test.
- Name test files `*Test.kt`; test methods can use backticked Given-When-Then phrases (see `CollectionServiceTest`).
- Integration checks in `api-server/src/test/kotlin/.../TaxApiIntegrationTest.kt`; keep H2 defaults and sample Excel data aligned.
- Add focused unit tests in module-local `src/test/kotlin`; avoid cross-module leaks in fixtures.

## Commit & Pull Request Guidelines
- Follow existing style: `<type>: <concise message>` (e.g., `refactor: CollectionProcessor 분리`, `test: CollectionProcessor 테스트 추가`). Types: feat, fix, refactor, test, docs, chore.
- One logical change per commit; mention module when useful (`api-server`, `collector`, etc.).
- PRs should summarize intent, link issue/task, list modules touched, and state test command(s) run. Include API/SQL diffs or screenshots for behavior changes.

## Security & Configuration Tips
- Auth is header-based (`X-Admin-Id`, `X-Admin-Role`) for prototype use only; do not reuse in production.
- Do not commit credentials; H2 stores data under `~/tax-data/taxdb.mv.db`.
- Apply overrides in `application.yml` per module rather than hardcoding constants; keep secrets externalized.
- Collector picks up only explicitly requested businesses (POST /collections). Stale requests are not auto-cleaned; monitor and reset as needed.
