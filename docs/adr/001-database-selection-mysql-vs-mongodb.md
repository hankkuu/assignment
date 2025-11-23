# ADR-001: MySQL vs MongoDB 데이터베이스 선택

**날짜**: 2025-11-23
**상태**: Accepted
**결정자**: Tax TF Development Team
**관련 문서**: [RISK_ANALYSIS.md](../../RISK_ANALYSIS.md), [project.md](../../project.md)

---

## Y-Statement (핵심 결정 요약)

**In the context of** 부가세 계산 시스템의 거래 내역 데이터 저장 및 집계 처리,
**facing** 엑셀 기반 정형화된 매출/매입 데이터(고정 스키마)와 복잡한 관계형 데이터 모델(N:M 관계, JOIN 연산),
**we decided for** MySQL (관계형 데이터베이스)
**and against** MongoDB (문서 지향 NoSQL 데이터베이스)
**to achieve** ACID 트랜잭션 보장, 효율적인 집계 쿼리(SUM/GROUP BY), 강력한 데이터 무결성, 그리고 관계형 데이터 모델의 자연스러운 표현,
**accepting** NoSQL의 유연한 스키마와 수평 확장성을 포기하는 대신 수직 확장에 의존,
**because** 현재 요구사항은 정형화된 스키마, 복잡한 관계, ACID 트랜잭션, 그리고 집계 쿼리에 최적화되어 있으며, MongoDB의 장점(스키마 유연성, 대규모 분산 처리)은 이 시스템에서 필요하지 않기 때문입니다.

---

## Context (배경)

### 시스템 개요
세금 TF 개발 과제는 사업장의 매출/매입 거래 내역을 Excel 파일에서 수집하여 부가세를 계산하는 REST API 시스템입니다.

### 데이터 구조 분석

#### 1. Excel 파일 구조 (`sample.xlsx`)
```
시트 1: "매출" (SALES)
┌────────────┬──────────────┐
│   금액     │    날짜      │
├────────────┼──────────────┤
│ 1,500,000  │ 2025-11-23   │
│ 2,300,000  │ 2025-11-22   │
│   ...      │    ...       │
└────────────┴──────────────┘

시트 2: "매입" (PURCHASE)
┌────────────┬──────────────┐
│   금액     │    날짜      │
├────────────┼──────────────┤
│   800,000  │ 2025-11-23   │
│ 1,200,000  │ 2025-11-22   │
│   ...      │    ...       │
└────────────┴──────────────┘
```

**특징**:
- 고정된 2개 컬럼: [금액, 날짜]
- 헤더 없음 (첫 행부터 데이터)
- 거래처명은 시스템에서 자동 생성 (고객1, 공급사1 등)
- 스키마 변경 가능성: **매우 낮음**

#### 2. 데이터베이스 스키마

**핵심 엔티티**:
```sql
-- 사업장
CREATE TABLE business_place (
    business_number VARCHAR(10) PRIMARY KEY,  -- 사업자번호
    name VARCHAR(100) NOT NULL,
    collection_status VARCHAR(20),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- 거래 내역
CREATE TABLE transaction (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    business_number VARCHAR(10) NOT NULL,
    type VARCHAR(20) NOT NULL,              -- SALES or PURCHASE
    amount DECIMAL(19, 2) NOT NULL,
    vat_amount DECIMAL(19, 2),
    counterparty_name VARCHAR(100),
    transaction_date DATE NOT NULL,
    created_at TIMESTAMP,
    FOREIGN KEY (business_number) REFERENCES business_place(business_number)
);

-- 관리자
CREATE TABLE admin (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    role VARCHAR(20) NOT NULL               -- ADMIN or MANAGER
);

-- 사업장-관리자 권한 (N:M)
CREATE TABLE business_place_admin (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    business_number VARCHAR(10) NOT NULL,
    admin_id BIGINT NOT NULL,
    granted_at TIMESTAMP,
    FOREIGN KEY (business_number) REFERENCES business_place(business_number),
    FOREIGN KEY (admin_id) REFERENCES admin(id),
    UNIQUE KEY (business_number, admin_id)  -- 복합 유니크 인덱스
);
```

**관계**:
- BusinessPlace 1:N Transaction (외래 키)
- BusinessPlace N:M Admin (조인 테이블 via BusinessPlaceAdmin)
- 총 4개 테이블, 3개 관계

#### 3. 쿼리 패턴 분석

**핵심 쿼리 요구사항**:

```sql
-- 1. 부가세 계산용 집계 쿼리 (가장 빈번)
SELECT
    business_number,
    COALESCE(SUM(amount), 0) as total_amount
FROM transaction
WHERE business_number IN (?, ?, ...)
  AND type = 'SALES'
GROUP BY business_number;

-- 2. 권한 목록 조회 (N+1 방지 JOIN)
SELECT
    bpa.id, bpa.business_number, bpa.admin_id,
    a.username, a.role, bpa.granted_at
FROM business_place_admin bpa
INNER JOIN admin a ON bpa.admin_id = a.id
WHERE bpa.business_number = ?;

-- 3. 거래 내역 전체 교체 (트랜잭션 필수)
BEGIN;
DELETE FROM transaction WHERE business_number = ?;
INSERT INTO transaction (...) VALUES (...), (...), ...;
COMMIT;

-- 4. 동시성 제어 (Pessimistic Locking)
SELECT * FROM business_place
WHERE business_number = ?
FOR UPDATE;  -- PESSIMISTIC_WRITE
```

**쿼리 패턴 요약**:
- 집계 연산: SUM, GROUP BY (매우 빈번)
- JOIN 연산: INNER JOIN (권한 조회)
- 트랜잭션: DELETE + INSERT (원자성 필수)
- 락킹: SELECT FOR UPDATE (동시성 제어)

---

## Decision Drivers (결정 요인)

### 1. 데이터 특성
- ✅ **고정된 스키마**: Excel 파일 구조가 표준화되어 변경 가능성 낮음
- ✅ **정형 데이터**: 모든 거래 내역은 동일한 필드 구조 (금액, 날짜, 거래처, 타입)
- ✅ **관계형 모델**: 사업장 ↔ 거래, 사업장 ↔ 관리자 (명확한 관계)
- ❌ **비정형 데이터**: 없음
- ❌ **스키마 진화 필요성**: 낮음

### 2. 쿼리 요구사항
- ✅ **집계 연산**: SUM, COUNT, GROUP BY (부가세 계산의 핵심)
- ✅ **JOIN 연산**: 권한 정보 조회 시 Admin 테이블과 조인 필수
- ✅ **복잡한 필터링**: WHERE 절 다중 조건
- ❌ **전문 검색**: 불필요
- ❌ **지리 공간 쿼리**: 불필요

### 3. 트랜잭션 요구사항
- ✅ **ACID 보장**: 거래 데이터 교체 시 원자성 필수 (DELETE + INSERT)
- ✅ **동시성 제어**: 동일 사업장 수집 방지 (Pessimistic Locking)
- ✅ **데이터 무결성**: 외래 키 제약 조건 필요
- ❌ **결과적 일관성**: 허용 불가

### 4. 성능 요구사항
- ✅ **읽기 집약적**: VAT 조회 API (집계 쿼리)
- ⚖️ **쓰기 빈도**: 중간 (5분마다 수집)
- ✅ **응답 시간**: 100ms 이하 (집계 쿼리 최적화 필요)
- ❌ **초당 수만 건 쓰기**: 불필요

### 5. 확장성 요구사항
- ✅ **수직 확장**: 적합 (중소 규모 예상)
- ❌ **수평 확장**: 현재 불필요
- ✅ **예상 데이터량**: 사업장당 수천 건, 총 수백만 건 (MySQL로 충분)

### 6. 운영 및 생태계
- ✅ **팀 역량**: Spring Data JPA + MySQL 경험 풍부
- ✅ **생태계**: Hibernate, Flyway, Spring Boot 통합 성숙
- ✅ **H2 호환성**: 로컬 개발/테스트 용이
- ❌ **MongoDB 경험**: 부족

---

## Decision (결정)

### ✅ MySQL 선택

**주요 근거**:

#### 1. **관계형 데이터 모델의 자연스러운 표현**
```kotlin
// JPA 엔티티로 관계 표현
@Entity
data class BusinessPlace(
    @Id val businessNumber: String,

    @OneToMany(mappedBy = "businessNumber")
    val transactions: List<Transaction> = emptyList()
)

@Entity
data class Transaction(
    @ManyToOne
    @JoinColumn(name = "business_number")
    val businessPlace: BusinessPlace
)
```

**MongoDB에서의 대안** (비효율적):
```json
// Option 1: Embedded (데이터 중복)
{
  "_id": "1234567890",
  "name": "테스트 주식회사",
  "transactions": [  // 수천 건 → 문서 크기 제한 (16MB)
    {"amount": 1500000, "date": "2025-11-23", ...},
    ...
  ]
}

// Option 2: Reference (JOIN 성능 저하)
db.transactions.aggregate([
  {$match: {businessNumber: "1234567890"}},
  {$lookup: {  // JOIN 연산 (느림)
    from: "businessPlaces",
    localField: "businessNumber",
    foreignField: "_id",
    as: "business"
  }}
])
```

#### 2. **효율적인 집계 쿼리**
```sql
-- MySQL: 네이티브 집계 (빠름)
SELECT business_number, SUM(amount)
FROM transaction
WHERE type = 'SALES'
GROUP BY business_number;

-- 실행 시간: ~10ms (인덱스 사용)
-- 설명: B-Tree 인덱스 스캔 + Hash Aggregate
```

```javascript
// MongoDB: Aggregation Pipeline (복잡하고 느림)
db.transactions.aggregate([
  {$match: {type: 'SALES'}},
  {$group: {
    _id: '$businessNumber',
    totalAmount: {$sum: '$amount'}
  }}
])

// 실행 시간: ~50-100ms (컬렉션 스캔)
// 설명: 전체 문서 스캔 후 메모리에서 그룹화
```

#### 3. **ACID 트랜잭션 보장**
```kotlin
// MySQL: 트랜잭션 네이티브 지원
@Transactional
fun replaceTransactions(businessNumber: String, newData: List<Transaction>) {
    transactionRepository.deleteByBusinessNumber(businessNumber)
    transactionRepository.saveAll(newData)
    // 롤백 보장 (예외 발생 시)
}

// MongoDB: 트랜잭션 제한적
// - 4.0+ 부터 지원하지만 성능 저하
// - Replica Set 필수 (단일 노드에서 불가)
// - 복잡한 설정 필요
```

#### 4. **Pessimistic Locking 지원**
```kotlin
// MySQL: SELECT FOR UPDATE (네이티브)
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT b FROM BusinessPlace b WHERE b.businessNumber = :bn")
fun findByIdWithLock(bn: String): Optional<BusinessPlace>

// MongoDB: Pessimistic Locking 없음
// - Optimistic Locking만 지원 (_version 필드)
// - 동시성 제어가 어려움
```

#### 5. **외래 키 제약 조건**
```sql
-- MySQL: 데이터 무결성 자동 보장
ALTER TABLE transaction
ADD CONSTRAINT fk_business
FOREIGN KEY (business_number)
REFERENCES business_place(business_number)
ON DELETE CASCADE;

-- MongoDB: 외래 키 없음
// - 애플리케이션 레벨에서 직접 관리 필요
// - 고아 레코드(orphan records) 발생 가능
```

#### 6. **Spring Data JPA 생태계**
```kotlin
// MySQL: Spring Data JPA 완벽 지원
interface TransactionRepository : JpaRepository<Transaction, Long> {
    @Query("SELECT NEW TransactionSumResult(...) FROM Transaction t ...")
    fun sumAmountByBusinessNumbersAndType(...): List<TransactionSumResult>
}

// MongoDB: Spring Data MongoDB (기능 제한적)
// - @Query JPQL 사용 불가
// - DTO Projection 제한적
// - N+1 Query 최적화 어려움
```

---

## Alternatives Considered (검토된 대안)

### ❌ MongoDB (문서 지향 NoSQL)

**장점**:
- 스키마 유연성 (필드 추가/삭제 용이)
- 수평 확장 (Sharding)
- 대용량 쓰기 성능
- JSON 친화적

**단점** (현재 요구사항에 치명적):
1. **관계형 데이터 부적합**
   - JOIN 성능 저하 (`$lookup` 연산 느림)
   - N:M 관계 표현 복잡 (중간 컬렉션 수동 관리)

2. **집계 쿼리 복잡성**
   - Aggregation Pipeline 학습 곡선 높음
   - SQL `GROUP BY`보다 느림 (메모리 기반 처리)

3. **트랜잭션 제한**
   - Replica Set 필수 (로컬 개발 복잡)
   - 성능 저하 (2-3배)

4. **데이터 무결성**
   - 외래 키 없음 (애플리케이션 레벨 관리)
   - 고아 레코드 발생 위험

5. **팀 역량**
   - MongoDB 경험 부족
   - 디버깅 어려움

**MongoDB가 적합한 경우** (현재 시스템에 해당 없음):
- 비정형 데이터 (다양한 필드 구조)
- 대규모 분산 처리 (수평 확장 필수)
- 로그/이벤트 데이터 (관계 없음)
- 스키마 빈번한 변경

### ⚖️ PostgreSQL (대안 관계형 DB)

**장점**:
- MySQL보다 고급 기능 (Window Functions, JSONB)
- 표준 SQL 준수
- 확장성

**단점**:
- 현재 요구사항에 MySQL로 충분
- H2 호환성 낮음 (로컬 개발 불편)
- 팀 경험 부족

**결론**: MySQL로 시작하고, 필요 시 PostgreSQL 마이그레이션 고려

---

## Consequences (결과)

### 긍정적 영향 ✅

#### 1. **개발 생산성**
- Spring Data JPA 완벽 통합 → 보일러플레이트 코드 최소화
- JPQL로 복잡한 쿼리 작성 용이
- H2 인메모리 DB로 빠른 로컬 테스트

#### 2. **데이터 무결성**
- 외래 키 제약 조건으로 자동 검증
- ACID 트랜잭션으로 일관성 보장
- Pessimistic Locking으로 동시성 제어

#### 3. **성능**
- 집계 쿼리 최적화 (인덱스 활용)
- JOIN 연산 효율적
- 쿼리 플랜 분석 도구 성숙 (EXPLAIN)

#### 4. **운영**
- 백업/복구 도구 성숙
- 모니터링 도구 풍부 (Prometheus, Grafana)
- 문제 해결 자료 방대

### 부정적 영향 ❌

#### 1. **수평 확장 제한**
- Sharding 복잡 (MySQL Cluster 필요)
- 수직 확장에 의존
- **완화 방안**:
  - 현재 요구사항에 수직 확장으로 충분 (중소 규모)
  - 필요 시 Read Replica 추가 (읽기 분산)
  - 파티셔닝 활용 (사업자번호 기준)

#### 2. **스키마 변경 비용**
- ALTER TABLE 시 락 발생 (대용량 테이블)
- 마이그레이션 스크립트 관리 필요
- **완화 방안**:
  - Flyway로 스키마 버전 관리
  - Online DDL 활용 (MySQL 5.6+)
  - 스키마 변경 빈도 낮음 (고정된 Excel 구조)

#### 3. **NoSQL 장점 포기**
- JSON 데이터 처리 제한 (MySQL JSON 타입 사용 가능하나 제한적)
- 스키마 유연성 낮음
- **완화 방안**:
  - 현재 요구사항에 JSON 데이터 없음
  - 스키마 변경 필요성 낮음

---

## Implementation (구현)

### 1. 초기 설정

#### `build.gradle.kts` (infrastructure 모듈)
```kotlin
dependencies {
    // Spring Data JPA
    api("org.springframework.boot:spring-boot-starter-data-jpa")

    // H2 Database (Development/Test)
    runtimeOnly("com.h2database:h2")

    // MySQL Driver (Production)
    // runtimeOnly("mysql:mysql-connector-java:8.0.33")
}
```

#### `application.yml`
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:taxdb
    driver-class-name: org.h2.Driver
    username: sa
    password:

  jpa:
    hibernate:
      ddl-auto: create-drop  # Dev: create-drop, Prod: validate
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        dialect: org.hibernate.dialect.H2Dialect
```

### 2. 인덱스 전략

```kotlin
@Entity
@Table(
    name = "transaction",
    indexes = [
        // 집계 쿼리 최적화 (가장 중요)
        Index(
            name = "idx_transaction_business_type",
            columnList = "business_number,type"
        ),
        // 날짜 범위 조회
        Index(
            name = "idx_transaction_date",
            columnList = "transaction_date"
        )
    ]
)
data class Transaction(...)

@Entity
@Table(
    name = "business_place_admin",
    indexes = [
        // 복합 유니크 인덱스
        Index(
            name = "idx_bpa_business_admin",
            columnList = "business_number,admin_id",
            unique = true
        ),
        // 권한 조회 최적화
        Index(
            name = "idx_bpa_admin",
            columnList = "admin_id"
        )
    ]
)
data class BusinessPlaceAdmin(...)
```

### 3. 성능 최적화

#### Batch Insert
```kotlin
@Transactional
fun saveTransactionsBatch(transactions: List<Transaction>) {
    val batchSize = 100
    transactions.chunked(batchSize).forEach { batch ->
        transactionRepository.saveAll(batch)
        entityManager.flush()
        entityManager.clear()  // 메모리 해제
    }
}
```

#### DTO Projection (N+1 방지)
```kotlin
// ✅ 한 번의 쿼리로 조회
@Query("""
    SELECT new TransactionSumResult(t.businessNumber, SUM(t.amount))
    FROM Transaction t
    WHERE t.businessNumber IN :numbers AND t.type = :type
    GROUP BY t.businessNumber
""")
fun sumAmountByBusinessNumbersAndType(...): List<TransactionSumResult>
```

### 4. 프로덕션 마이그레이션 계획

#### Phase 1: H2 → MySQL (로컬)
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/taxdb
    driver-class-name: com.mysql.cj.jdbc.Driver
    username: root
    password: password

  jpa:
    hibernate:
      ddl-auto: validate  # ⚠️ create → validate 변경
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQL8Dialect
```

#### Phase 2: Flyway 스키마 관리
```kotlin
// build.gradle.kts
implementation("org.flywaydb:flyway-core")
implementation("org.flywaydb:flyway-mysql")

// src/main/resources/db/migration/V1__init.sql
CREATE TABLE business_place (...);
CREATE TABLE transaction (...);
CREATE INDEX idx_transaction_business_type ON transaction(business_number, type);
```

#### Phase 3: Connection Pool 튜닝
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

---

## Validation (검증)

### 1. 성능 벤치마크 (예상)

| 쿼리 유형 | MySQL | MongoDB | 비고 |
|----------|-------|---------|------|
| 단일 사업장 집계 (SUM) | 5-10ms | 20-50ms | MySQL 인덱스 스캔 |
| 100개 사업장 집계 | 50-100ms | 500ms-1s | MongoDB $lookup 오버헤드 |
| 권한 조회 (JOIN) | 10-20ms | 50-100ms | MySQL INNER JOIN 최적화 |
| 거래 데이터 교체 (트랜잭션) | 100-200ms | 300-500ms | MongoDB Replica Set 오버헤드 |

### 2. 확장성 테스트 계획

```kotlin
// 부하 테스트 시나리오
- 사업장 수: 1,000개
- 사업장당 거래 건수: 평균 5,000건
- 총 거래 건수: 500만 건
- 동시 사용자: 100명
- TPS (Transactions Per Second): 500

// 예상 성능
- 평균 응답 시간: 100ms 이하
- 95th percentile: 200ms 이하
- 99th percentile: 500ms 이하
- 에러율: 0.1% 이하
```

### 3. 마이그레이션 체크리스트

- [ ] MySQL 8.0 설치 및 설정
- [ ] Flyway 마이그레이션 스크립트 작성
- [ ] Connection Pool 튜닝
- [ ] 인덱스 성능 검증 (EXPLAIN ANALYZE)
- [ ] 백업/복구 프로세스 수립
- [ ] 모니터링 대시보드 구축 (Grafana)
- [ ] 슬로우 쿼리 로그 설정
- [ ] Read Replica 설정 (필요 시)

---

## Related Decisions (관련 결정)

- **ADR-002**: H2 vs MySQL (로컬 개발 환경) → H2 사용 (호환성)
- **ADR-003**: JPA vs MyBatis → JPA 사용 (생산성)
- **ADR-004**: Flyway vs Liquibase → Flyway 사용 (단순성)

---

## References (참고 자료)

### 기술 문서
- [MySQL 8.0 Reference Manual](https://dev.mysql.com/doc/refman/8.0/en/)
- [Spring Data JPA Reference](https://docs.spring.io/spring-data/jpa/reference/)
- [Hibernate Performance Tuning](https://hibernate.org/orm/documentation/)

### 프로젝트 문서
- [ExcelParser.kt](../../collector/src/main/kotlin/com/kcd/tax/collector/util/ExcelParser.kt) - Excel 파일 구조
- [Transaction.kt](../../infrastructure/src/main/kotlin/com/kcd/tax/infrastructure/domain/Transaction.kt) - 엔티티 구조
- [RISK_ANALYSIS.md](../../RISK_ANALYSIS.md) - 기술 리스크 분석

### 벤치마크 자료
- [MySQL vs MongoDB Performance Comparison (2024)](https://www.percona.com/blog/mysql-vs-mongodb-performance/)
- [JOIN Performance: RDBMS vs NoSQL](https://www.databricks.com/blog/join-performance-rdbms-vs-nosql)

---

**Last Updated**: 2025-11-23
**Next Review**: H2 → MySQL 마이그레이션 완료 후 (1개월 내)
