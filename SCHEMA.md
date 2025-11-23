# 데이터베이스 스키마 설계

## 개요

세금 TF 개발 과제의 데이터베이스 스키마 문서입니다.

- **DBMS**: H2 Database (file-based)
- **Connection**: `jdbc:h2:file:~/tax-data/taxdb;AUTO_SERVER=TRUE`
- **JPA 전략**: `ddl-auto: create` (개발 환경), `ddl-auto: none` (collector)

---

## ERD (Entity Relationship Diagram)

```
┌─────────────────────┐
│      admin          │
├─────────────────────┤
│ id (PK)            │
│ username           │
│ role               │  ─────┐
│ created_at         │       │
└─────────────────────┘       │
                              │ N
                              │
                              │
┌─────────────────────────────┼──────────────┐
│   business_place_admin      │              │
├─────────────────────────────┤              │
│ id (PK)                    │              │
│ business_number (FK)  ─────┼─────┐        │
│ admin_id (FK)         ─────┘     │ N      │
│ granted_at                 │     │        │
└────────────────────────────┘     │        │
                                   │        │
                                   │        │
┌──────────────────────────────────┼────────┘
│     business_place               │
├──────────────────────────────────┤
│ business_number (PK)            │
│ name                            │
│ collection_status               │
│ created_at                      │
│ updated_at                      │
└──────────────────────────────────┘
                  │
                  │ 1
                  │
                  │ N
┌──────────────────────────────────┐
│      transaction                 │
├──────────────────────────────────┤
│ id (PK)                         │
│ business_number (FK)            │
│ type                            │
│ amount                          │
│ vat_amount                      │
│ counterparty_name               │
│ transaction_date                │
│ created_at                      │
└──────────────────────────────────┘
```

---

## 테이블 상세 설명

### 1. admin (관리자)

관리자 정보를 저장하는 테이블입니다.

| 컬럼명 | 타입 | 제약조건 | 설명 |
|-------|------|---------|------|
| **id** | BIGINT | PK, AUTO_INCREMENT | 관리자 ID |
| username | VARCHAR(50) | NOT NULL, UNIQUE | 관리자 사용자명 |
| role | VARCHAR(20) | NOT NULL | 권한 (ADMIN/MANAGER) |
| created_at | TIMESTAMP | NOT NULL | 생성일시 |

**Indexes**:
- `PRIMARY KEY (id)`
- `UNIQUE INDEX idx_admin_username (username)`

**Sample Data**:
```sql
INSERT INTO admin (id, username, role, created_at) VALUES
    (1, 'admin1', 'ADMIN', CURRENT_TIMESTAMP),
    (2, 'manager1', 'MANAGER', CURRENT_TIMESTAMP),
    (3, 'manager2', 'MANAGER', CURRENT_TIMESTAMP);
```

---

### 2. business_place (사업장)

사업장 정보 및 수집 상태를 저장하는 테이블입니다.

| 컬럼명 | 타입 | 제약조건 | 설명 |
|-------|------|---------|------|
| **business_number** | VARCHAR(10) | PK | 사업자번호 (10자리) |
| name | VARCHAR(100) | NOT NULL | 사업장명 |
| collection_status | VARCHAR(20) | NOT NULL | 수집 상태 (NOT_REQUESTED/COLLECTING/COLLECTED) |
| collection_requested_at | TIMESTAMP | NULL | 수집 요청 시각 (요청 후 Collector 폴링 대상) |
| created_at | TIMESTAMP | NOT NULL | 생성일시 |
| updated_at | TIMESTAMP | NOT NULL | 수정일시 |

**Indexes**:
- `PRIMARY KEY (business_number)`
- `INDEX idx_business_place_status (collection_status)` - 수집 상태별 조회 성능 향상
- 필요 시 `collection_requested_at`에 보조 인덱스 추가 가능 (요청 대기 조회 최적화)

**Constraints**:
- `business_number`: 정확히 10자리 숫자
- `collection_status`: ENUM 타입 (NOT_REQUESTED, COLLECTING, COLLECTED)

**Sample Data**:
```sql
INSERT INTO business_place (business_number, name, collection_status, created_at, updated_at) VALUES
    ('1234567890', '테스트 주식회사', 'NOT_REQUESTED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('0987654321', '샘플 상사', 'NOT_REQUESTED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('1111111111', '데모 기업', 'NOT_REQUESTED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
```

---

### 3. business_place_admin (사업장-관리자 권한)

사업장과 관리자의 N:M 관계를 관리하는 조인 테이블입니다.

| 컬럼명 | 타입 | 제약조건 | 설명 |
|-------|------|---------|------|
| **id** | BIGINT | PK, AUTO_INCREMENT | 권한 ID |
| business_number | VARCHAR(10) | NOT NULL, FK | 사업자번호 |
| admin_id | BIGINT | NOT NULL, FK | 관리자 ID |
| granted_at | TIMESTAMP | NOT NULL | 권한 부여일시 |

**Indexes**:
- `PRIMARY KEY (id)`
- `UNIQUE INDEX idx_bpa_business_admin (business_number, admin_id)` - 중복 권한 방지
- `INDEX idx_bpa_business (business_number)` - 사업장별 관리자 조회
- `INDEX idx_bpa_admin (admin_id)` - 관리자별 사업장 조회

**Foreign Keys**:
- `business_number → business_place(business_number)` ON DELETE CASCADE
- `admin_id → admin(id)` ON DELETE CASCADE

**Sample Data**:
```sql
INSERT INTO business_place_admin (id, business_number, admin_id, granted_at) VALUES
    (1, '1234567890', 2, CURRENT_TIMESTAMP),
    (2, '0987654321', 2, CURRENT_TIMESTAMP),
    (3, '0987654321', 3, CURRENT_TIMESTAMP);
```

---

### 4. transaction (거래 내역)

매출/매입 거래 내역을 저장하는 테이블입니다.

| 컬럼명 | 타입 | 제약조건 | 설명 |
|-------|------|---------|------|
| **id** | BIGINT | PK, AUTO_INCREMENT | 거래 ID |
| business_number | VARCHAR(10) | NOT NULL, FK | 사업자번호 |
| type | VARCHAR(20) | NOT NULL | 거래 타입 (SALES/PURCHASE) |
| amount | DECIMAL(15,2) | NOT NULL | 거래 금액 (공급가액) |
| vat_amount | DECIMAL(15,2) | NULL | 부가세 금액 |
| counterparty_name | VARCHAR(200) | NOT NULL | 거래처명 |
| transaction_date | DATE | NOT NULL | 거래일자 |
| created_at | TIMESTAMP | NOT NULL | 생성일시 |

**Indexes**:
- `PRIMARY KEY (id)`
- `INDEX idx_transaction_business (business_number)` - 사업장별 거래 조회
- `INDEX idx_transaction_business_type (business_number, type)` - 사업장+타입별 조회
- `INDEX idx_transaction_date (transaction_date)` - 기간별 조회

**Foreign Keys**:
- `business_number → business_place(business_number)` ON DELETE CASCADE

**Constraints**:
- `type`: ENUM 타입 (SALES, PURCHASE)
- `amount`: 양수만 허용

**Note**:
- `amount`: 공급가액 (부가세 제외)
- `vat_amount`: 부가세 (선택 사항)
- Excel 파싱 시 자동으로 생성됨 (sample.xlsx 기준)

---

## 주요 설계 결정사항

### 1. 사업자번호를 Primary Key로 사용

**결정**: `business_place` 테이블의 PK를 `business_number`(VARCHAR(10))로 설정

**이유**:
- 사업자번호는 법적으로 고유한 식별자
- 자연 키(Natural Key) 사용으로 의미 명확
- 외래 키 조인 시 직관적 (별도 ID 조회 불필요)
- 도메인 규칙 강제 (10자리 숫자)

**트레이드오프**:
- VARCHAR로 인한 약간의 성능 저하 (BIGINT 대비)
- 하지만 사업장 수가 많지 않아 무시할 수 있는 수준

### 2. N:M 관계 처리

**결정**: `business_place_admin` 조인 테이블 사용

**이유**:
- 한 사업장에 여러 관리자 할당 가능
- 한 관리자가 여러 사업장 관리 가능
- 권한 부여/삭제 이력 추적 가능 (`granted_at`)

**설계 특징**:
- Surrogate Key (`id`) 사용: JPA 매핑 편의성
- Unique Constraint (`business_number`, `admin_id`): 중복 권한 방지
- Cascade Delete: 사업장/관리자 삭제 시 권한도 자동 삭제

### 3. 상태 관리 (State Machine)

**결정**: `collection_status`를 ENUM으로 관리

**상태 전이 규칙**:
```
NOT_REQUESTED --(request sets requested_at)--> COLLECTING → COLLECTED
      ↓                            ↓
      ←───────────────(reset)──────←─────── (실패 시 복원, requested_at 초기화)
```

**구현**:
- JPA: `@Enumerated(EnumType.STRING)`
- 도메인 메서드: `startCollection()`(요청 시각 필요), `completeCollection()`, `resetCollection()` (요청 시각 초기화)
- 상태 전이 검증: `require` 문으로 불변식 보호

### 4. 부가세 계산 최적화

**결정**: `transaction` 테이블에 개별 거래만 저장, 부가세는 실시간 계산

**이유**:
- 부가세는 `(매출 합계 - 매입 합계) × 1/11`로 계산
- 미리 계산해서 저장하면 데이터 정합성 문제 발생 가능
- 거래 데이터만 저장하고, 조회 시 실시간 계산

**성능 고려**:
- `idx_transaction_business_type` 인덱스로 집계 쿼리 최적화
- 필요 시 캐싱(Redis) 또는 Materialized View 도입 가능

---

## 데이터 정합성

### Referential Integrity (참조 무결성)

1. `business_place_admin.business_number` → `business_place.business_number`
2. `business_place_admin.admin_id` → `admin.id`
3. `transaction.business_number` → `business_place.business_number`

**Cascade 정책**:
- 사업장 삭제 시 → 권한, 거래 내역 모두 삭제 (CASCADE)
- 관리자 삭제 시 → 권한만 삭제 (CASCADE)

### Business Rules (비즈니스 규칙)

1. **사업자번호**: 정확히 10자리 숫자 (애플리케이션 레벨에서 검증)
2. **상태 전이**: 도메인 메서드로만 가능 (`startCollection()`, etc.)
3. **권한 중복 방지**: UNIQUE 제약 조건 + 애플리케이션 체크
4. **거래 금액**: 양수만 허용 (애플리케이션 레벨에서 검증)

---

## 인덱스 전략

### 성능 최적화를 위한 인덱스

1. **수집 상태별 조회**: `idx_business_place_status`
   - Collector가 NOT_REQUESTED 상태 조회 시 사용
   - Polling 방식으로 10초마다 조회

2. **사업장별 거래 집계**: `idx_transaction_business_type`
   - 부가세 계산 시 `WHERE business_number = ? AND type = ?` 사용
   - **N+1 쿼리 해결**: Type-safe DTO로 일괄 조회 (task-7)
   - `sumAmountByBusinessNumbersAndType()` 메서드에서 사용

3. **관리자 권한 조회**: `idx_bpa_admin`
   - MANAGER가 자신의 사업장 목록 조회 시 사용

4. **거래 기간별 조회**: `idx_transaction_date`
   - 향후 기간별 집계 기능 추가 시 사용

### N+1 쿼리 최적화 인덱스 (task-7)

5. **복합 인덱스**: `idx_bpa_business_admin`
   - UNIQUE constraint로도 사용
   - `findDetailsByBusinessNumber()` JOIN 쿼리에서 활용
   - 권한 조회 시 1 + N → 1 query로 최적화

---

## DDL Scripts

전체 DDL은 다음 위치에서 확인 가능합니다:
- **Schema**: `api-server/src/main/resources/schema.sql` (JPA auto-ddl 사용 시 자동 생성)
- **Initial Data**: `api-server/src/main/resources/data.sql`

---

## 동시성 제어 및 락킹 전략

### 현재 구조 동시성 분석

#### 1. 수집 상태 변경 (collection_status + collection_requested_at)

**시나리오**: 여러 요청이 동시에 같은 사업장에 대해 수집을 요청

```kotlin
@Transactional
fun requestCollection(businessNumber: String): CollectionStatus {
    val businessPlace = repository.findById(businessNumber)
        .orElseThrow { NotFoundException(...) }

    if (businessPlace.collectionStatus != NOT_REQUESTED) throw ConflictException(...)
    if (businessPlace.collectionRequestedAt != null) throw ConflictException(...) // 이미 대기

    businessPlace.collectionRequestedAt = LocalDateTime.now()
    repository.save(businessPlace)
    return businessPlace.collectionStatus
}
```

**폴링 조건**: Collector는 `collection_status = NOT_REQUESTED` **and** `collection_requested_at IS NOT NULL` 인 건만 처리

```kotlin
// Collector start (동시성 제어)
@Transactional
fun start(businessNumber: String) {
    val bp = repository.findByBusinessNumberForUpdate(businessNumber)
        ?: throw IllegalStateException()
    bp.startCollection() // requestedAt 필요, NOT_REQUESTED만 허용
    repository.save(bp)
}
```

**락 전략**: BusinessPlaceRepository에 `findByBusinessNumberForUpdate` (PESSIMISTIC_WRITE) 적용하여 중복 시작 방지

**SQL 쿼리**:
```sql
-- PESSIMISTIC_WRITE
SELECT * FROM business_place WHERE business_number = ? FOR UPDATE
```

#### 2. 거래 내역 삽입 (transaction)

**시나리오**: Collector가 Excel 데이터를 파싱하여 대량 삽입

**현재 구현**:
```kotlin
// ExcelParser에서 파싱
val transactions = parseExcelFile(filePath, businessNumber)

// 기존 데이터 삭제 후 재삽입
transactionRepository.deleteByBusinessNumber(businessNumber)
transactionRepository.saveAll(transactions)
```

**동시성 이슈**:
- Collector는 하나의 사업장당 한 번만 수집 (상태가 COLLECTING이면 재요청 불가)
- 따라서 **락 불필요**

**단, 주의사항**:
- 향후 "재수집" 기능 추가 시 비관적 락 필요
- 부가세 조회와 동시에 수집이 일어나면 부정확한 데이터 조회 가능
  - 현재는 허용 (Eventual Consistency)
  - 엄격한 정합성 필요 시 낙관적 락 고려

#### 3. 권한 부여/삭제 (business_place_admin)

**시나리오**: ADMIN이 동시에 같은 관리자에게 같은 사업장 권한 부여

**현재 구현**:
```kotlin
fun grantPermission(businessNumber: String, adminId: Long) {
    // 중복 체크
    if (businessPlaceAdminRepository.existsByBusinessNumberAndAdminId(businessNumber, adminId)) {
        throw ConflictException("이미 권한이 존재합니다")
    }

    // 권한 부여
    val permission = BusinessPlaceAdmin(businessNumber, adminId)
    repository.save(permission)
}
```

**보호 메커니즘**:
- **UNIQUE 제약 조건**: `UNIQUE (business_number, admin_id)`
- 데이터베이스 레벨에서 중복 방지

**동시성 이슈**:
- Race Condition 발생 가능하지만, DB 제약 조건이 최종 방어선
- 중복 삽입 시도 시 `DataIntegrityViolationException` 발생
- 애플리케이션에서 예외 처리하여 사용자에게 안내

**락 필요성**: **불필요** (DB 제약 조건으로 충분)

### 락킹 전략 권장사항

| 작업 | 락 유형 | 이유 | 우선순위 |
|------|---------|------|---------|
| **수집 상태 변경** | **비관적 락** | Race Condition 방지, 단순성 | **높음** ⚠️ |
| 거래 내역 삽입 | 락 불필요 | 상태 검증으로 중복 수집 방지 | 낮음 |
| 권한 부여/삭제 | 락 불필요 | UNIQUE 제약 조건 활용 | 낮음 |
| 부가세 조회 | 락 불필요 | Read-only, Dirty Read 허용 | 낮음 |

### 비관적 락 vs 낙관적 락 비교

#### 비관적 락 (Pessimistic Lock) - **권장**

**장점**:
- 즉시 충돌 감지 및 실패 처리
- 구현 간단 (`@Lock` 애노테이션)
- Lost Update 완전 방지

**단점**:
- 락 대기로 인한 성능 저하 가능
- 데드락 위험 (하지만 단일 테이블 락이므로 위험 낮음)

**적용 코드**:
```kotlin
// BusinessPlaceRepository.kt
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT bp FROM BusinessPlace bp WHERE bp.businessNumber = :businessNumber")
fun findByIdForUpdate(@Param("businessNumber") businessNumber: String): BusinessPlace?

// CollectionService.kt
@Transactional
fun requestCollection(businessNumber: String): CollectionStatus {
    val businessPlace = repository.findByIdForUpdate(businessNumber)
        ?: throw NotFoundException(...)

    businessPlace.startCollection()  // 상태 전이 (검증 포함)
    return businessPlace.collectionStatus
}
```

#### 낙관적 락 (Optimistic Lock) - **미권장 (현재 구조)**

**장점**:
- 락 없이 동작, 높은 성능
- 충돌이 드문 경우 효율적

**단점**:
- `@Version` 컬럼 추가 필요
- 충돌 시 재시도 로직 필요
- 사용자 경험 저하 (충돌 시 실패 후 재시도 요청)

**적용 방법** (참고용):
```kotlin
@Entity
class BusinessPlace(
    @Id
    val businessNumber: String,

    @Version  // 낙관적 락용 버전 컬럼
    var version: Long = 0,

    // ...
)

// 충돌 시 OptimisticLockException 발생
// 애플리케이션에서 catch하여 재시도 또는 에러 응답
```

**미권장 이유**:
- 수집 요청은 5분 소요되므로 "재시도"가 부적절
- 사용자는 즉시 실패를 알아야 함
- 버전 컬럼 추가로 스키마 복잡도 증가

### 구현 우선순위

**현재 필수**:
1. ✅ **비관적 락 적용**: `BusinessPlace.collection_status` 변경 시

**향후 고려**:
2. 재수집 기능 추가 시 거래 내역 락킹
3. 대규모 트래픽 대비 낙관적 락 전환 검토
4. Read Replica 도입 시 Replication Lag 처리

---

## 향후 확장 고려사항

### 1. 감사 로그 (Audit Log)
- 권한 변경 이력 추적
- 수집 실패 로그 저장

### 2. 소프트 삭제 (Soft Delete)
- `deleted_at` 컬럼 추가
- 실제 데이터는 보존, 논리적 삭제만 수행

### 3. 파티셔닝
- `transaction` 테이블을 `transaction_date` 기준으로 파티셔닝
- 대용량 데이터 처리 시 성능 향상

### 4. 읽기 전용 복제본
- 부가세 조회 API용 Read Replica 구성
- 수집 작업과 조회 작업 분리
