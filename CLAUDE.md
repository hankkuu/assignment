# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

세금 TF 개발 과제 - 사업장의 매출/매입 데이터를 비동기로 수집하고 부가세를 계산하는 Spring Boot 기반 REST API 시스템입니다.

**Key Features**:
- 사업장 관리 (CRUD: Create, Read, Update - 4 APIs)
- 데이터 수집 요청 및 상태 조회 (2 APIs)
- 사업장 권한 관리 (ADMIN → MANAGER, 3 APIs)
- 부가세 계산 및 조회 (권한별 필터링, 1 API)
- 총 **10개 REST API 엔드포인트**

**Tech Stack**: Kotlin 1.9 + Spring Boot 3.5 + Spring Data JPA + H2 Database

**Architecture**: 4-module structure (common, infrastructure, api-server, collector)

## Build & Run Commands

```bash
# Build all modules
./gradlew clean build

# Run API Server (port 8080)
./gradlew :api-server:bootRun

# Run Collector (port 8081) - separate terminal
./gradlew :collector:bootRun

# Run specific module
./gradlew :common:build
./gradlew :infrastructure:build

# Build without tests
./gradlew clean build -x test

# Run tests only
./gradlew test

# Check compilation
./gradlew compileKotlin
```

**H2 Console**: http://localhost:8080/h2-console (JDBC URL: `jdbc:h2:file:~/tax-data/taxdb;AUTO_SERVER=TRUE`, Username: `sa`, Password: empty)
Note: File-based H2 with AUTO_SERVER mode - allows API server and Collector to share database concurrently

## Architecture Overview

### Layered Architecture Pattern

```
Controller Layer (HTTP/REST)
    ↓ DTO 변환
Service Layer (Business Logic)
    ↓ 도메인 객체
Repository Layer (Data Access)
    ↓ JPA
Database (H2)
```

**Cross-Cutting Concerns**:
- **Security**: `AdminAuthInterceptor` → `AuthContext` (ThreadLocal) → `@RequireAuth`/`@RequireAdmin` annotations
- **Exception Handling**: `GlobalExceptionHandler` catches all exceptions and returns `ErrorResponse`
- **Async Processing**: `@Async` + `ThreadPoolTaskExecutor` (configured in `AsyncConfig`)
- **AOP Logging**: `ControllerLoggingAspect` automatically logs all API calls (request/response/error with duration)

## Architecture Benefits (4-Module Structure)

### Separation of Concerns
```
┌─────────────────────────────────────────────┐
│        Application Modules                  │
│  (api-server, collector)                    │
│  - HTTP handlers, schedulers                │
└───────────────────┬─────────────────────────┘
                    │ depends on
┌───────────────────▼─────────────────────────┐
│        Infrastructure Module                │
│  - JPA Entities                             │
│  - Repository implementations               │
│  - Technical utilities                      │
└───────────────────┬─────────────────────────┘
                    │ depends on
┌───────────────────▼─────────────────────────┐
│        Common Module                        │
│  - Pure business domain (Enums)             │
│  - Exception types                          │
│  - No framework dependencies                │
└─────────────────────────────────────────────┘
```

### Benefits:
1. **Testability**: Can test common module without any infrastructure
2. **Flexibility**: Easy to swap persistence technology (e.g., MongoDB)
3. **Clear Boundaries**: Business logic separated from technical details
4. **Reusability**: Multiple applications can share infrastructure

### Key Design Decisions

#### 1. VAT Calculation Logic (VatCalculator.kt)

**CRITICAL**: 부가세 계산은 **1의 자리에서 반올림하여 10원 단위로 처리**합니다.

```kotlin
// Formula: (매출 - 매입) × 1/11
// Rounding: 12345.12 → 12350 (1의 자리 반올림)

val vatRounded = vat.setScale(0, RoundingMode.HALF_UP)  // 소수점 반올림
val result = vatRounded
    .divide(BigDecimal.TEN, 1, RoundingMode.HALF_UP)   // ÷10
    .setScale(0, RoundingMode.HALF_UP)                  // 1의 자리 반올림
    .multiply(BigDecimal.TEN)                           // ×10 → 10원 단위
```

Example: `(10,000,000 - 5,000,000) × 1/11 = 454,545.45... → 454,545 → 454,550`

#### 2. Authentication via HTTP Headers (Prototype Security)

⚠️ **This is NOT production-ready security** - headers can be easily forged.

```
X-Admin-Id: {adminId}           # Required
X-Admin-Role: {ADMIN|MANAGER}   # Required
```

- `AdminAuthInterceptor` extracts headers and stores in `AuthContext` (ThreadLocal)
- `@RequireAuth`: Authentication required
- `@RequireAdmin`: ADMIN role required (auto-checks ADMIN vs MANAGER)
- `AuthContext.clear()` is called in `afterCompletion()` to prevent memory leaks

#### 3. Async Collection Processing

**Flow**: NOT_REQUESTED → COLLECTING → COLLECTED

```kotlin
@Async  // Runs in separate thread pool
@Transactional
fun collectData(businessNumber: String) {
    // 1. Change status to COLLECTING
    // 2. Thread.sleep(5 * 60 * 1000)  // 5-minute simulation
    // 3. Parse Excel file (sample.xlsx) via ExcelParser.parseExcelFile()
    // 4. Delete old transactions, insert new ones
    // 5. Change status to COLLECTED
    // On failure: Reset status to NOT_REQUESTED
}
```

- **Executor**: 5 core threads, 10 max threads, 100 queue capacity
- **Conflict Prevention**: Returns 409 if already COLLECTING
- **Failure Handling**: Auto-rollback to NOT_REQUESTED on error

#### 4. Permission-Based Access Control

**N:M Relationship**: BusinessPlace ↔ Admin (via `BusinessPlaceAdmin` join table)

- **ADMIN**:
  - Can view ALL business places
  - Can grant/revoke permissions
  - Can perform all operations

- **MANAGER**:
  - Can ONLY view assigned business places
  - `VatCalculationService.checkPermission()` enforces this
  - Queries `BusinessPlaceAdminRepository` to verify access

## Multi-Module Architecture Guide

### Current Implementation Status

✅ **The project is implemented as a multi-module architecture** following the requirement: **"API 서버와 수집기로 구성"** (composed of API server and collector).

This multi-module architecture provides:
- Clear separation of concerns (API vs Collection)
- Independent deployment and scaling
- Fault isolation
- Better testability
- Framework-agnostic domain layer

### Multi-Module Structure

```
tax/
├── settings.gradle.kts          # Multi-module configuration
├── build.gradle.kts             # Root build script
│
├── common/                      # Shared module
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/kcd/tax/common/
│       ├── enums/               # CollectionStatus, AdminRole, TransactionType
│       └── exception/           # ErrorCode, BusinessException
│
├── infrastructure/              # persistence or etc module
│   └── src/main/kotlin/com/kcd/tax/infrastructure/
│       ├── domain/              # JPA Entity (BusinessPlace, Admin, Transaction, etc.)
│       ├── repository/          # JPA Repository interfaces
│       └── util/                # VatCalculator, ExcelParser
│
├── api-server/                  # REST API Server
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/kcd/tax/api/
│       ├── TaxApiApplication.kt
│       ├── controller/          # REST Controllers
│       ├── service/             # CollectionService, VatService, BusinessPlaceService
│       ├── dto/                 # Request/Response DTOs
│       ├── security/            # AuthContext, Interceptor, annotations
│       └── config/              # WebConfig, JpaConfig
│
└── collector/                   # Data Collector
    ├── build.gradle.kts
    └── src/main/kotlin/com/kcd/tax/collector/
        ├── CollectorApplication.kt
        ├── service/             # CollectorService (actual collection logic)
        ├── scheduler/           # ScheduledCollectionPoller
        └── config/              # AsyncConfig, JpaConfig
```

### Module Responsibilities

#### `common` Module
- **Purpose**: Pure domain enums and exceptions (framework-agnostic)
- **Dependencies**: SLF4J only (NO Spring dependencies)
- **Exports**: Enums (CollectionStatus, AdminRole, TransactionType), BusinessException, ErrorCode
- **Note**: Pure Kotlin module - can be used in any JVM project without framework lock-in

#### `infrastructure` Module 
- **Purpose**: Persistence layer and technical infrastructure
- **Dependencies**: common + Spring Data JPA + H2 + Apache POI
- **Exports**:
  - JPA Entities with business logic
  - Repository interfaces for data access
  - Technical utilities (ExcelParser, VatCalculator)
- **Note**: Depends on common for Enums and Exceptions

#### `api-server` Module
- **Purpose**: Handle HTTP requests, authentication, authorization
- **Dependencies**: `infrastructure` module (which transitively includes `common`)
- **Direct Dependencies**: Spring Web, Jackson Kotlin
- **Responsibilities**:
  - Accept collection requests → change status to NOT_REQUESTED
  - Query collection status
  - Manage permissions (ADMIN only)
  - Calculate and return VAT
- **Port**: 8080

#### `collector` Module
- **Purpose**: Execute actual data collection jobs
- **Dependencies**: `infrastructure` module (which includes JPA, H2, Apache POI)
- **Responsibilities**:
  - Poll database for NOT_REQUESTED jobs every 10 seconds
  - Execute 5-minute collection simulation
  - Parse Excel data (via ExcelParser from infrastructure)
  - Update status to COLLECTED
- **Port**: 8081 (Spring Boot app with scheduler, no REST endpoints)

### Module Communication Strategy

#### Option 1: Database Polling (Simple, No Infrastructure)

**Collector polls for work**:
```kotlin
// collector/scheduler/ScheduledCollectionPoller.kt
@Component
class ScheduledCollectionPoller(
    private val businessPlaceRepository: BusinessPlaceRepository,
    private val collectorService: CollectorService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 10000)  // Poll every 10 seconds
    fun pollAndCollect() {
        val pendingJobs = businessPlaceRepository
            .findByCollectionStatus(CollectionStatus.NOT_REQUESTED)

        if (pendingJobs.isNotEmpty()) {
            logger.info("Found ${pendingJobs.size} pending collection jobs")
            pendingJobs.forEach { job ->
                collectorService.collectData(job.businessNumber)
            }
        }
    }
}
```

**API Server just updates status**:
```kotlin
// api-server/service/CollectionService.kt
@Transactional
fun requestCollection(businessNumber: String): CollectionStatus {
    val businessPlace = businessPlaceRepository.findById(businessNumber)
        .orElseThrow { NotFoundException(...) }

    // Just mark as requested - collector will pick it up
    if (businessPlace.collectionStatus == CollectionStatus.COLLECTING) {
        throw ConflictException(ErrorCode.COLLECTION_ALREADY_IN_PROGRESS)
    }

    // Status stays NOT_REQUESTED - collector will change to COLLECTING
    // Or introduce new status: PENDING → COLLECTING → COLLECTED
    return businessPlace.collectionStatus
}
```

**Pros**: Simple, no infrastructure needed
**Cons**: Polling delay, not real-time, DB load

#### Option 2: Message Queue (Production-Ready)

**Add RabbitMQ/Kafka dependency**:
```kotlin
// common/build.gradle.kts
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-amqp")
}
```

**API Server publishes events**:
```kotlin
// api-server/service/CollectionService.kt
@Transactional
fun requestCollection(businessNumber: String): CollectionStatus {
    val businessPlace = businessPlaceRepository.findById(businessNumber)
        .orElseThrow { NotFoundException(...) }

    if (businessPlace.collectionStatus == CollectionStatus.COLLECTING) {
        throw ConflictException(ErrorCode.COLLECTION_ALREADY_IN_PROGRESS)
    }

    // Publish to queue
    rabbitTemplate.convertAndSend(
        "tax.collection.queue",
        CollectionRequestEvent(businessNumber)
    )

    return CollectionStatus.NOT_REQUESTED
}
```

**Collector listens to queue**:
```kotlin
// collector/listener/CollectionRequestListener.kt
@Component
class CollectionRequestListener(
    private val collectorService: CollectorService
) {
    @RabbitListener(queues = ["tax.collection.queue"])
    fun handleCollectionRequest(event: CollectionRequestEvent) {
        collectorService.collectData(event.businessNumber)
    }
}
```

**Pros**: Real-time, scalable, decoupled
**Cons**: Infrastructure complexity (RabbitMQ/Kafka required)

### Gradle Multi-Module Setup

#### Root `settings.gradle.kts`
```kotlin
rootProject.name = "tax"

include("common")
include("infrastructure")
include("api-server")
include("collector")
```

#### Root `build.gradle.kts`
```kotlin
plugins {
    kotlin("jvm") version "1.9.25" apply false
    kotlin("plugin.spring") version "1.9.25" apply false
    kotlin("plugin.jpa") version "1.9.25" apply false
    id("org.springframework.boot") version "3.5.7" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

allprojects {
    group = "com.kcd"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.spring")
    apply(plugin = "org.springframework.boot")
    apply(plugin = "io.spring.dependency-management")

    dependencies {
        "implementation"("org.jetbrains.kotlin:kotlin-reflect")
        "implementation"("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
```

#### `common/build.gradle.kts`
```kotlin
// 순수 Kotlin 모듈 - Spring 의존성 없음
dependencies {
    // SLF4J for logging in exceptions
    api("org.slf4j:slf4j-api:2.0.9")

    // Test
    testImplementation("org.jetbrains.kotlin:kotlin-test")
}

// No bootJar task - this is a library module
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

tasks.named<Jar>("jar") {
    enabled = true
}
```

#### `infrastructure/build.gradle.kts`
```kotlin
plugins {
    kotlin("plugin.jpa")
}

dependencies {
    // Common 모듈 의존성
    api(project(":common"))

    // Spring Data JPA
    api("org.springframework.boot:spring-boot-starter-data-jpa")

    // H2 Database
    api("com.h2database:h2")

    // Apache POI (Excel 파싱)
    api("org.apache.poi:poi:5.2.3")
    api("org.apache.poi:poi-ooxml:5.2.3")

    // Validation
    api("org.springframework.boot:spring-boot-starter-validation")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

// No bootJar task - this is a library module
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

tasks.named<Jar>("jar") {
    enabled = true
}
```

#### `api-server/build.gradle.kts`
```kotlin
plugins {
    id("org.springframework.boot")
    kotlin("plugin.jpa")
}

dependencies {
    implementation(project(":infrastructure"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.mockk:mockk:1.13.8")
}
```

#### `collector/build.gradle.kts`
```kotlin
plugins {
    id("org.springframework.boot")
    kotlin("plugin.jpa")
}

dependencies {
    implementation(project(":infrastructure"))

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
```

### Step-by-Step Refactoring Guide

#### Phase 1: Create Module Structure
```bash
# 1. Create module directories
mkdir -p common/src/main/kotlin/com/kcd/tax/common
mkdir -p infrastructure/src/main/kotlin/com/kcd/tax/infrastructure
mkdir -p api-server/src/main/kotlin/com/kcd/tax/api
mkdir -p collector/src/main/kotlin/com/kcd/tax/collector

# 2. Create build.gradle.kts for each module
touch common/build.gradle.kts
touch infrastructure/build.gradle.kts
touch api-server/build.gradle.kts
touch collector/build.gradle.kts

# 3. Update root settings.gradle.kts and build.gradle.kts
```

#### Phase 2: Organize Modules by Layer

```bash
# Move pure domain enums/exceptions to common
mv src/main/kotlin/com/kcd/tax/domain/enums common/src/main/kotlin/com/kcd/tax/common/
mv src/main/kotlin/com/kcd/tax/exception common/src/main/kotlin/com/kcd/tax/common/

# Move JPA entities, repositories, and utilities to infrastructure
mv src/main/kotlin/com/kcd/tax/domain infrastructure/src/main/kotlin/com/kcd/tax/infrastructure/
mv src/main/kotlin/com/kcd/tax/repository infrastructure/src/main/kotlin/com/kcd/tax/infrastructure/
mv src/main/kotlin/com/kcd/tax/util infrastructure/src/main/kotlin/com/kcd/tax/infrastructure/

# Update package names
# enums/* → com.kcd.tax.common.enums.*
# exception/* → com.kcd.tax.common.exception.*
# domain/* → com.kcd.tax.infrastructure.domain.*
# repository/* → com.kcd.tax.infrastructure.repository.*
# util/* → com.kcd.tax.infrastructure.util.*
```

#### Phase 3: Split Services
```bash
# API Server gets:
# - CollectionService (request handling only)
# - VatCalculationService
# - BusinessPlaceService

mv src/main/kotlin/com/kcd/tax/service/CollectionService.kt \
   api-server/src/main/kotlin/com/kcd/tax/api/service/

# Collector gets:
# - CollectorService (actual collection logic)
mv src/main/kotlin/com/kcd/tax/service/CollectorService.kt \
   collector/src/main/kotlin/com/kcd/tax/collector/service/
```

#### Phase 4: Move Controllers & Security to API Server
```bash
mv src/main/kotlin/com/kcd/tax/controller api-server/src/main/kotlin/com/kcd/tax/api/
mv src/main/kotlin/com/kcd/tax/security api-server/src/main/kotlin/com/kcd/tax/api/
mv src/main/kotlin/com/kcd/tax/dto api-server/src/main/kotlin/com/kcd/tax/api/
```

#### Phase 5: Create Application Classes
```kotlin
// api-server/src/main/kotlin/com/kcd/tax/api/TaxApiApplication.kt
@SpringBootApplication(scanBasePackages = ["com.kcd.tax.api", "com.kcd.tax.common"])
@EntityScan(basePackages = ["com.kcd.tax.infrastructure.domain"])
@EnableJpaRepositories(basePackages = ["com.kcd.tax.infrastructure.repository"])
class TaxApiApplication

fun main(args: Array<String>) {
    runApplication<TaxApiApplication>(*args)
}

// collector/src/main/kotlin/com/kcd/tax/collector/CollectorApplication.kt
@SpringBootApplication(scanBasePackages = ["com.kcd.tax.collector", "com.kcd.tax.common"])
@EntityScan(basePackages = ["com.kcd.tax.infrastructure.domain"])
@EnableJpaRepositories(basePackages = ["com.kcd.tax.infrastructure.repository"])
@EnableScheduling
class CollectorApplication

fun main(args: Array<String>) {
    runApplication<CollectorApplication>(*args)
}
```

#### Phase 6: Configure Application Properties
```yaml
# api-server/src/main/resources/application.yml
server:
  port: 8080

# collector/src/main/resources/application.yml
server:
  port: 8081  # Or disable if no HTTP needed

spring:
  task:
    scheduling:
      pool:
        size: 5
```

#### Phase 7: Test & Build
```bash
# Build all modules
./gradlew clean build

# Run API server
./gradlew :api-server:bootRun

# Run collector (in separate terminal)
./gradlew :collector:bootRun

# Test multi-module
curl -X POST http://localhost:8080/api/v1/collections ...
# Check collector logs for pickup
```

### Migration Checklist

- [x] Create multi-module Gradle structure (4 modules: common, infrastructure, api-server, collector)
- [x] Move enums and exceptions to `common` (framework-agnostic)
- [x] Move domain entities, repositories, utilities to `infrastructure`
- [x] Update package imports across all moved files
- [x] Split CollectionService (API) vs CollectorService (actual collection)
- [x] Move controllers/security to `api-server`
- [x] Implement DB polling in `collector` (every 10 seconds)
- [x] Create separate application.yml for each application module
- [x] Configure shared H2 database with AUTO_SERVER mode
- [x] Add @EntityScan and @EnableJpaRepositories to Application classes
- [x] Test API server independently (port 8080)
- [x] Test collector independently (port 8081)
- [x] Test end-to-end flow (API request → Collector pickup → Status change)
- [x] Update documentation with multi-module structure

**Implementation Status**: ✅ All items completed - project is fully multi-modular

### Build Commands for Multi-Module

```bash
# Build all modules
./gradlew clean build

# Build specific module
./gradlew :common:build
./gradlew :api-server:build
./gradlew :collector:build

# Run API server
./gradlew :api-server:bootRun

# Run collector
./gradlew :collector:bootRun

# Run both (use separate terminals)
./gradlew :api-server:bootRun &
./gradlew :collector:bootRun
```

## Domain Model Critical Rules

### BusinessPlace Entity State Machine

```kotlin
// Valid transitions enforced by domain methods:
fun startCollection()     // NOT_REQUESTED → COLLECTING (throws if not NOT_REQUESTED)
fun completeCollection()  // COLLECTING → COLLECTED (throws if not COLLECTING)
fun resetCollection()     // * → NOT_REQUESTED (for error recovery)
```

**DO NOT** directly set `collectionStatus` - always use these methods to ensure state integrity.

### Business Number as Natural Key

`BusinessPlace` uses `businessNumber` (String, 10 digits) as PK - not an auto-generated ID. This is intentional for domain clarity.

## Testing API

All endpoints require auth headers (`X-Admin-Id` and `X-Admin-Role`).

### Business Place Management (ADMIN only)

```bash
# Create business place
curl -X POST http://localhost:8080/api/v1/business-places \
  -H "Content-Type: application/json" \
  -H "X-Admin-Id: 1" \
  -H "X-Admin-Role: ADMIN" \
  -d '{
    "businessNumber": "2222222222",
    "name": "신규 사업장"
  }'

# List all business places
curl http://localhost:8080/api/v1/business-places \
  -H "X-Admin-Id: 1" \
  -H "X-Admin-Role: ADMIN"

# Get business place detail
curl http://localhost:8080/api/v1/business-places/2222222222 \
  -H "X-Admin-Id: 1" \
  -H "X-Admin-Role: ADMIN"

# Update business place
curl -X PUT http://localhost:8080/api/v1/business-places/2222222222 \
  -H "Content-Type: application/json" \
  -H "X-Admin-Id: 1" \
  -H "X-Admin-Role: ADMIN" \
  -d '{
    "name": "수정된 사업장명"
  }'
```

### Collection APIs

```bash
# Request collection
curl -X POST http://localhost:8080/api/v1/collections \
  -H "Content-Type: application/json" \
  -H "X-Admin-Id: 1" \
  -H "X-Admin-Role: ADMIN" \
  -d '{"businessNumber": "1234567890"}'

# Check collection status
curl http://localhost:8080/api/v1/collections/1234567890/status \
  -H "X-Admin-Id: 1" \
  -H "X-Admin-Role: ADMIN"
```

### Permission Management (ADMIN only)

```bash
# Grant permission
curl -X POST http://localhost:8080/api/v1/business-places/1234567890/admins \
  -H "Content-Type: application/json" \
  -H "X-Admin-Id: 1" \
  -H "X-Admin-Role: ADMIN" \
  -d '{"adminId": 2}'

# List permissions
curl http://localhost:8080/api/v1/business-places/1234567890/admins \
  -H "X-Admin-Id: 1" \
  -H "X-Admin-Role: ADMIN"

# Revoke permission
curl -X DELETE http://localhost:8080/api/v1/business-places/1234567890/admins/2 \
  -H "X-Admin-Id: 1" \
  -H "X-Admin-Role: ADMIN"
```

### VAT Query

```bash
# Query VAT (ADMIN sees all)
curl http://localhost:8080/api/v1/vat \
  -H "X-Admin-Id: 1" \
  -H "X-Admin-Role: ADMIN"

# Query VAT for specific business
curl "http://localhost:8080/api/v1/vat?businessNumber=1234567890" \
  -H "X-Admin-Id: 1" \
  -H "X-Admin-Role: ADMIN"

# Query VAT (MANAGER sees only assigned)
curl http://localhost:8080/api/v1/vat \
  -H "X-Admin-Id: 2" \
  -H "X-Admin-Role: MANAGER"
```

## Initial Test Data (data.sql)

**Admins**:
- ID=1: admin1 (ADMIN)
- ID=2: manager1 (MANAGER) - can access 1234567890, 0987654321
- ID=3: manager2 (MANAGER) - can access 0987654321

**Business Places**:
- 1234567890: 테스트 주식회사
- 0987654321: 샘플 상사
- 1111111111: 데모 기업

## Sample Data File (sample.xlsx)

The project includes `sample.xlsx` in the root directory for data collection simulation.

**File Structure**:
- **Sheets**: "매출" (Sales - 412 records), "매입" (Purchase - 42 records)
- **Columns**: 금액 (Amount) | 날짜 (Date) - **No header row**
- **Format**:
  - Amount: Numeric (e.g., 147000, 235500)
  - Date: Date format (e.g., 2025-08-01)
- **Counterparty Names**: Auto-generated during parsing (고객1, 공급사1, etc.)

**Data Statistics**:
| Type | Records | Total Amount |
|------|---------|--------------|
| Sales (매출) | 412 | 47,811,032원 |
| Purchase (매입) | 42 | 1,406,700원 |
| **Expected VAT** | - | **4,218,580원** |

**How It Works**:
1. Collector reads `sample.xlsx` via `ExcelParser.parseExcelFile()`
2. Parses both sheets starting from row 0 (no header)
3. Auto-generates counterparty names (고객1, 고객2... / 공급사1, 공급사2...)
4. Creates `Transaction` entities and saves to database
5. VAT calculation uses aggregated amounts: `(Sales Total - Purchase Total) × 1/11`

**Configuration**:
```yaml
# collector/src/main/resources/application.yml
collector:
  data-file: sample.xlsx  # File path can be configured
```

## Database Schema

For detailed database schema documentation, refer to `SCHEMA.md` in the project root.

### Tables (4)

1. **admin**: Administrator information
   - PK: `id` (BIGINT, AUTO_INCREMENT)
   - Fields: `username`, `role` (ADMIN/MANAGER), `created_at`

2. **business_place**: Business place information and collection status
   - PK: `business_number` (VARCHAR(10)) - **Natural Key**
   - Fields: `name`, `collection_status`, `created_at`, `updated_at`
   - Index: `idx_business_place_status` for collection status queries

3. **business_place_admin**: N:M relationship between business places and admins
   - PK: `id` (BIGINT, AUTO_INCREMENT)
   - FK: `business_number` → business_place, `admin_id` → admin
   - Fields: `granted_at`
   - Unique constraint: `(business_number, admin_id)`

4. **transaction**: Sales/Purchase transaction records
   - PK: `id` (BIGINT, AUTO_INCREMENT)
   - FK: `business_number` → business_place
   - Fields: `type` (SALES/PURCHASE), `amount`, `vat_amount`, `counterparty_name`, `transaction_date`
   - Indexes: `idx_transaction_business`, `idx_transaction_business_type`

### Key Design Decisions

- **Business Number as PK**: Natural key for domain clarity
- **N:M Relationship**: One business can have multiple admins, one admin can manage multiple businesses
- **CASCADE DELETE**: When business is deleted, related permissions and transactions are auto-deleted
- **Optimized Indexes**: For collection status polling, VAT calculation aggregation

## API Endpoints Summary

Total: **10 endpoints** (fully implements project.pdf CRUD requirements)

### Business Place Management (ADMIN) - 4 endpoints
- `POST   /api/v1/business-places` - Create
- `GET    /api/v1/business-places` - List all
- `GET    /api/v1/business-places/{businessNumber}` - Get detail
- `PUT    /api/v1/business-places/{businessNumber}` - Update

### Collection - 2 endpoints
- `POST   /api/v1/collections` - Request collection
- `GET    /api/v1/collections/{businessNumber}/status` - Check status

### Permission Management (ADMIN) - 3 endpoints
- `POST   /api/v1/business-places/{businessNumber}/admins` - Grant
- `GET    /api/v1/business-places/{businessNumber}/admins` - List
- `DELETE /api/v1/business-places/{businessNumber}/admins/{adminId}` - Revoke

### VAT Query - 1 endpoint
- `GET    /api/v1/vat?businessNumber={businessNumber}` - Calculate VAT

## Common Development Patterns

### Adding a New API Endpoint

1. Create DTO in `dto/request` or `dto/response`
2. Add method to appropriate Service
3. Add Controller method with `@RequireAuth` (and `@RequireAdmin` if needed)
4. Test with curl using headers

### Working with Permissions

```kotlin
// In Service layer:
val adminId = AuthContext.getAdminId()
val role = AuthContext.getAdminRole()

// Check permission for specific business:
vatCalculationService.checkPermission(businessNumber, adminId, role)
```

### Error Handling

Throw business exceptions - they're auto-caught by `GlobalExceptionHandler`:

```kotlin
throw NotFoundException(ErrorCode.BUSINESS_NOT_FOUND, "custom message")
throw ConflictException(ErrorCode.COLLECTION_ALREADY_IN_PROGRESS)
throw ForbiddenException("No access to this business")
```

Returns standardized `ErrorResponse` with code, message, timestamp.

## Important Constraints

1. **DTO Usage**: Controllers must never expose Entity classes directly - always convert via DTO
2. **Transaction Boundaries**: `@Transactional` is at Service layer, not Repository
3. **ThreadLocal Cleanup**: Security interceptor MUST call `AuthContext.clear()` in `afterCompletion()`
4. **State Validation**: Use domain methods (`startCollection()`, `completeCollection()`) to enforce state machine
5. **BigDecimal for Money**: Always use `BigDecimal` for amounts, never `Double` or `Float`

## Recent Code Quality Improvements

### 1. AOP-based Logging (task-6)
- **ControllerLoggingAspect**: Centralized logging for all API endpoints
- **Benefits**: DRY principle, consistent log format, automatic performance tracking
- **Log Format**:
  - `[API_REQUEST] GET /api/v1/vat?businessNumber=123`
  - `[API_RESPONSE] GET /api/v1/vat status=200 duration=25ms`
  - `[API_ERROR] POST /api/v1/collections error=ConflictException duration=5ms`

### 2. N+1 Query Resolution & Type-safe DTOs (task-7)
- **BusinessPlaceAdminRepository.findDetailsByBusinessNumber()**: JOIN query eliminates N+1
- **TransactionRepository**: Returns `TransactionSumResult` instead of `Array<Any>`
- **Benefits**: Performance optimization, type safety, no casting errors

### 3. Security Enhancements (task-7)
- **ExcelParser**: Path Traversal attack prevention
  - Validates file paths, blocks "..", "./", ".\" patterns
  - Canonicalizes paths before processing
- **Parameterized logging**: SLF4J best practices throughout

### 4. Controller Separation (task-7)
- **BusinessPlaceAdminController**: Extracted from BusinessPlaceController
- **Follows SRP**: CRUD operations vs Permission management
- **RESTful pattern**: `/api/v1/business-places/{businessNumber}/admins`

### 5. Pagination Refactoring (task-8)
- **PageableHelper utility**: Reusable memory-based pagination
- **VatCalculationService.calculateVatWithPaging()**: Business logic moved to service layer
- **VatController**: Simplified from 33 lines to 10 lines
- **Benefits**: Separation of concerns, testability, maintainability

## Known Limitations

- **Security**: Header-based auth is for prototype only - production needs JWT/OAuth2
- **Async Monitoring**: No way to track async job progress beyond database status polling
- **No Caching**: VAT calculations are not cached - consider Redis for production
- **Memory-based Pagination**: For large datasets, consider DB-level pagination (LIMIT/OFFSET)
- **5-Minute Wait**: Actual collection uses `Thread.sleep()` - production should use message queue

## Package Structure Logic

```
api-server/
├── controller/          # HTTP layer, DTOs in/out, auth annotations
│   ├── dto/
│   │   ├── request/    # Validation via @Valid
│   │   └── response/   # Serialized to JSON
│   ├── VatController.kt
│   ├── BusinessPlaceController.kt
│   └── BusinessPlaceAdminController.kt  # Separated for SRP
├── service/            # Business logic, transactions, pagination
│   ├── VatCalculationService.kt  # calculateVatWithPaging()
│   ├── BusinessPlaceService.kt
│   └── CollectionService.kt
├── security/           # AuthContext, Interceptor, annotations
├── aspect/            # AOP cross-cutting concerns
│   └── ControllerLoggingAspect.kt  # Centralized API logging
├── util/              # Application-level utilities
│   └── PageableHelper.kt  # Memory-based pagination
└── config/            # Spring configuration (Web, Security)

infrastructure/
├── domain/             # JPA Entities with business rules
├── repository/         # JPA queries, custom JPQL with DTOs
│   ├── BusinessPlaceAdminDetail  # Type-safe DTO
│   └── TransactionSumResult      # Type-safe DTO
└── util/              # Technical utilities
    ├── VatCalculator.kt
    └── ExcelParser.kt  # with Path Traversal protection

common/
├── enums/             # Status, Role, Type enums
└── exception/         # ErrorCode, custom exceptions
```

When adding features, follow this structure strictly - it maintains separation of concerns.

---

## Project Deliverables

### Source Code
- Multi-module Gradle project (common, infrastructure, api-server, collector)
- Complete implementation of project.pdf requirements
- Total 10 REST API endpoints (CRUD fully implemented)

### Documentation
- **README.md**: Project introduction and setup guide
- **project.md**: Detailed requirements analysis and implementation explanation
- **SCHEMA.md**: Database schema design documentation
- **CLAUDE.md**: This file - developer guidance for Claude Code
- **IMPLEMENTATION_SUMMARY.md**: Requirements fulfillment status

### Data Files
- **sample.xlsx**: Sample data for collection (454 transactions)

### Build & Test
```bash
./gradlew clean build -x test  # Build all modules
./gradlew :api-server:bootRun  # Run API server
./gradlew :collector:bootRun   # Run collector
```

All requirements from project.pdf have been fully implemented and verified.
