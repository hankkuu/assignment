# 세금 TF 개발 과제 - 구현 문서

## 📋 목차
1. [과제 요구사항](#1-과제-요구사항)
2. [구현 개요](#2-구현-개요)
3. [아키텍처 설계](#3-아키텍처-설계)
4. [기술 스택](#4-기술-스택)
5. [데이터베이스 설계](#5-데이터베이스-설계)
6. [API 명세](#6-api-명세)
7. [실행 방법](#7-실행-방법)
8. [주요 설계 결정사항](#8-주요-설계-결정사항)

---

## 1. 과제 요구사항

### 1.1 목표
- **주요 목표**: API로 수집 요청을 하고 수집기에서 수집된 데이터로 부가세를 계산하여 각자의 권한에 맞게 보여줄 수 있어야 함
- **권한 관리**:
  - ADMIN/MANAGER 권한 존재
  - 권한 체크는 header에 관리자 정보 설정하여 체크 가능

### 1.2 범위

#### API 서버
1. **수집 요청 API**
   - 사업자 번호(10자리)를 받아 수집기로 요청 전송
   - 수집은 최대 5분까지 소요

2. **수집 상태 조회 API**
   - 데이터 수집 상태 조회
   - 상태: `NOT_REQUESTED`, `COLLECTING`, `COLLECTED`

3. **사업장 권한 관리 API**
   - CRUD 필요
   - ADMIN만 사용 가능
   - 한 사업장은 여러 관리자가 관리 가능 (N:M 관계)

4. **부가세 조회 API**
   - 계산 로직: `(매출 금액 합계 - 매입 금액 합계) × 1/11`
   - **1의 자리에서 반올림** (예: 12345.12 → 12350)
   - ADMIN: 전체 사업장 조회 가능
   - MANAGER: 권한이 부여된 사업장만 조회 가능

#### 수집기
- sample 문서의 매출/매입 탭 데이터를 DB에 적재
- 실제 수집 시뮬레이션: 5분 후 작업 완료 처리
- 상태 변경 플로우: `NOT_REQUESTED` → `COLLECTING` → `COLLECTED`

### 1.3 기술 스택 요구사항
- Spring / Spring Boot
- JPA
- Kotlin (권장)
- RDB 연동 및 설계

---

## 2. 구현 개요

### 2.1 구현 방식
본 프로젝트는 요구사항의 **"API 서버와 수집기"** 구성을 명확히 구분하기 위해 **멀티 모듈 아키텍처**로 구현되었습니다.

### 2.2 모듈 구성
```
tax/
├── common/           # 순수 도메인 계층 (Enums, Exceptions)
├── infrastructure/   # 기술 인프라 계층 (JPA, Repository, Utilities)
├── api-server/       # REST API 서버 (포트 8080)
└── collector/        # 데이터 수집기 (포트 8081)
```

### 2.3 핵심 특징
- ✅ **명확한 책임 분리**: API 서버와 수집기가 독립적인 Spring Boot 애플리케이션
- ✅ **비동기 처리**: Database Polling 방식으로 API 서버와 수집기 통신
- ✅ **권한 기반 접근 제어**: Header 기반 인증 + ThreadLocal을 활용한 컨텍스트 관리
- ✅ **도메인 주도 상태 관리**: Entity 메서드를 통한 상태 전이 강제

---

## 3. 아키텍처 설계

### 3.1 시스템 구성도

```
┌─────────────┐   HTTP   ┌──────────────┐        ┌──────────────┐
│   Client    │─────────▶│  API Server  │        │  Collector   │
└─────────────┘          │  (Port 8080) │        │ (Port 8081)  │
                         └──────┬───────┘        └──────┬───────┘
                                │                        │
                                │   ┌────────────────┐   │
                                └──▶│  H2 Database   │◀──┘
                                    │ (File-based)   │
                                    │ AUTO_SERVER    │
                                    └────────────────┘
```

### 3.2 4-Layer 멀티모듈 아키텍처

```
┌─────────────────────────────────────────────┐
│   Application Layer (api-server, collector) │
│   - HTTP endpoints, Schedulers              │
└───────────────────┬─────────────────────────┘
                    │ depends on
┌───────────────────▼─────────────────────────┐
│   Infrastructure Layer (infrastructure)     │
│   - JPA Entities, Repositories              │
│   - Technical Utilities (VatCalculator)     │
└───────────────────┬─────────────────────────┘
                    │ depends on
┌───────────────────▼─────────────────────────┐
│   Domain Layer (common)                     │
│   - Pure Kotlin (Enums, Exceptions)         │
│   - NO framework dependencies               │
└─────────────────────────────────────────────┘
```

### 3.3 API 서버와 수집기 통신 방식

#### Database Polling 방식 (현재 구현)

**장점**: 간단, 별도 인프라 불필요
**단점**: 10초 폴링 지연

```kotlin
// Collector: 10초마다 DB 폴링
@Scheduled(fixedDelay = 10000)
fun pollAndCollect() {
    val pendingJobs = businessPlaceRepository
        .findByCollectionStatus(CollectionStatus.NOT_REQUESTED)

    pendingJobs.forEach { job ->
        collectorService.collectData(job.businessNumber)
    }
}

// API Server: 상태만 변경
fun requestCollection(businessNumber: String) {
    businessPlace.collectionStatus = CollectionStatus.NOT_REQUESTED
    // Collector가 자동으로 감지하여 처리
}
```

---

## 4. 기술 스택

### 4.1 실제 적용 스택

| 분류 | 기술 | 버전 | 선택 이유 |
|------|------|------|----------|
| **언어** | Kotlin | 1.9.25 | Null Safety, 간결성, Spring 완벽 호환 |
| **프레임워크** | Spring Boot | 3.5.7 | 최신 안정 버전, 생산성 |
| **ORM** | Spring Data JPA | 3.5.x | 표준 ORM, Repository 패턴 |
| **데이터베이스** | H2 Database | 2.x | 임베디드 DB, 빠른 프로토타이핑 |
| **빌드 도구** | Gradle Kotlin DSL | 8.14.3 | 멀티모듈 지원, 타입 안전 |
| **Excel 파싱** | Apache POI | 5.2.3 | 엑셀 데이터 처리 표준 라이브러리 |
| **테스트** | JUnit 5, MockK | - | Kotlin 친화적 테스트 프레임워크 |
| **Java** | JDK | 21 LTS | 최신 LTS 버전 |

### 4.2 주요 의존성 구조

```kotlin
common → SLF4J only (프레임워크 독립적)
infrastructure → common + Spring Data JPA + H2 + Apache POI
api-server → infrastructure + Spring Web
collector → infrastructure + Spring Scheduling
```

---

## 5. 데이터베이스 설계

### 5.1 ERD

```
┌─────────────────┐         ┌──────────────────────┐         ┌─────────────┐
│  business_place │────┬───▶│ business_place_admin │◀───┬────│    admin    │
│  (PK: 사업자번호)│    │    │  (권한 매핑 테이블)    │    │    │ (관리자)     │
└─────────────────┘    │    └──────────────────────┘    │    └─────────────┘
        │              │                                 │
        │              └─────────── N:M ─────────────────┘
        ▼
┌─────────────────┐
│   transaction   │
│  (거래 내역)     │
└─────────────────┘
```

### 5.2 테이블 상세

#### business_place (사업장)
```sql
CREATE TABLE business_place (
    business_number VARCHAR(10) PRIMARY KEY,  -- 사업자번호 (자연키)
    name VARCHAR(100) NOT NULL,
    collection_status VARCHAR(20) NOT NULL DEFAULT 'NOT_REQUESTED',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,

    INDEX idx_status (collection_status)  -- 수집기 폴링 최적화
);
```

**설계 결정**: 사업자번호를 PK로 사용 (도메인 의미 명확, 불변성 보장)

#### admin (관리자)
```sql
CREATE TABLE admin (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    role VARCHAR(20) NOT NULL,  -- ADMIN, MANAGER
    created_at TIMESTAMP NOT NULL,

    INDEX idx_username (username)
);
```

#### business_place_admin (권한 매핑)
```sql
CREATE TABLE business_place_admin (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    business_number VARCHAR(10) NOT NULL,
    admin_id BIGINT NOT NULL,
    granted_at TIMESTAMP NOT NULL,

    FOREIGN KEY (business_number) REFERENCES business_place(business_number) ON DELETE CASCADE,
    FOREIGN KEY (admin_id) REFERENCES admin(id) ON DELETE CASCADE,
    UNIQUE (business_number, admin_id),  -- 중복 방지

    INDEX idx_business (business_number),
    INDEX idx_admin (admin_id)
);
```

**설계 결정**: 복합키 대신 대리키 사용 (JPA 친화적)

#### transaction (거래 내역)
```sql
CREATE TABLE transaction (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    business_number VARCHAR(10) NOT NULL,
    type VARCHAR(20) NOT NULL,  -- SALES, PURCHASE
    amount DECIMAL(15, 2) NOT NULL,
    vat_amount DECIMAL(15, 2),
    counterparty_name VARCHAR(100),
    transaction_date DATE NOT NULL,
    created_at TIMESTAMP NOT NULL,

    FOREIGN KEY (business_number) REFERENCES business_place(business_number) ON DELETE CASCADE,

    INDEX idx_business_type (business_number, type)  -- 부가세 계산 최적화
);
```

**설계 결정**: DECIMAL(15,2) 사용 (금액 정확성 보장)

### 5.3 샘플 데이터
```sql
-- 관리자
INSERT INTO admin (id, username, role) VALUES
    (1, 'admin1', 'ADMIN'),
    (2, 'manager1', 'MANAGER'),
    (3, 'manager2', 'MANAGER');

-- 사업장
INSERT INTO business_place (business_number, name, collection_status) VALUES
    ('1234567890', '테스트 주식회사', 'NOT_REQUESTED'),
    ('0987654321', '샘플 상사', 'NOT_REQUESTED'),
    ('1111111111', '데모 기업', 'NOT_REQUESTED');

-- 권한 (manager1은 2개, manager2는 1개 사업장)
INSERT INTO business_place_admin (business_number, admin_id) VALUES
    ('1234567890', 2),
    ('0987654321', 2),
    ('0987654321', 3);
```

---

## 6. API 명세

### 6.1 공통 사항

#### 인증 헤더
모든 API 요청 시 필수:
```
X-Admin-Id: {adminId}
X-Admin-Role: {ADMIN|MANAGER}
```

⚠️ **보안 주의**: 현재는 프로토타입으로 헤더 기반 인증 사용. 운영 환경에서는 JWT/OAuth2 적용 필요.

#### 공통 에러 응답
```json
{
  "errorCode": "ERROR_CODE",
  "message": "에러 메시지",
  "timestamp": "2025-01-01T00:00:00"
}
```

### 6.2 수집 요청 API

#### POST /api/v1/collections
사업장 데이터 수집을 요청합니다.

**Request**:
```bash
curl -X POST http://localhost:8080/api/v1/collections \
  -H "Content-Type: application/json" \
  -H "X-Admin-Id: 1" \
  -H "X-Admin-Role: ADMIN" \
  -d '{"businessNumber": "1234567890"}'
```

**Response (200 OK)**:
```json
{
  "businessNumber": "1234567890",
  "status": "NOT_REQUESTED",
  "message": "수집 요청이 접수되었습니다."
}
```

**Error Cases**:
- `400 BAD_REQUEST`: 잘못된 사업자번호 형식
- `404 NOT_FOUND`: 존재하지 않는 사업장
- `409 CONFLICT`: 이미 수집 중인 사업장

### 6.3 수집 상태 조회 API

#### GET /api/v1/collections/{businessNumber}/status

**Request**:
```bash
curl http://localhost:8080/api/v1/collections/1234567890/status \
  -H "X-Admin-Id: 1" \
  -H "X-Admin-Role: ADMIN"
```

**Response (200 OK)**:
```json
{
  "businessNumber": "1234567890",
  "status": "COLLECTED",
  "timestamp": "2025-01-01T12:05:00"
}
```

**상태 값**:
- `NOT_REQUESTED`: 수집 요청 전 또는 대기 중
- `COLLECTING`: 수집 진행 중 (최대 5분)
- `COLLECTED`: 수집 완료

### 6.4 사업장 관리 API (ADMIN 전용)

#### POST /api/v1/business-places
사업장 생성

**Request**:
```bash
curl -X POST http://localhost:8080/api/v1/business-places \
  -H "Content-Type: application/json" \
  -H "X-Admin-Id: 1" \
  -H "X-Admin-Role: ADMIN" \
  -d '{
    "businessNumber": "2222222222",
    "name": "신규 사업장"
  }'
```

**Response (201 Created)**:
```json
{
  "businessNumber": "2222222222",
  "name": "신규 사업장",
  "collectionStatus": "NOT_REQUESTED",
  "createdAt": "2025-01-01T12:00:00",
  "updatedAt": "2025-01-01T12:00:00"
}
```

#### GET /api/v1/business-places
사업장 목록 조회

**Response (200 OK)**:
```json
[
  {
    "businessNumber": "1234567890",
    "name": "테스트 주식회사",
    "collectionStatus": "COLLECTED",
    "createdAt": "2025-01-01T10:00:00",
    "updatedAt": "2025-01-01T11:30:00"
  },
  {
    "businessNumber": "0987654321",
    "name": "샘플 상사",
    "collectionStatus": "NOT_REQUESTED",
    "createdAt": "2025-01-01T10:00:00",
    "updatedAt": "2025-01-01T10:00:00"
  }
]
```

#### GET /api/v1/business-places/{businessNumber}
사업장 상세 조회

**Response (200 OK)**:
```json
{
  "businessNumber": "1234567890",
  "name": "테스트 주식회사",
  "collectionStatus": "COLLECTED",
  "createdAt": "2025-01-01T10:00:00",
  "updatedAt": "2025-01-01T11:30:00"
}
```

#### PUT /api/v1/business-places/{businessNumber}
사업장 정보 수정

**Request**:
```bash
curl -X PUT http://localhost:8080/api/v1/business-places/1234567890 \
  -H "Content-Type: application/json" \
  -H "X-Admin-Id: 1" \
  -H "X-Admin-Role: ADMIN" \
  -d '{
    "name": "수정된 사업장명"
  }'
```

**Response (200 OK)**:
```json
{
  "businessNumber": "1234567890",
  "name": "수정된 사업장명",
  "collectionStatus": "COLLECTED",
  "createdAt": "2025-01-01T10:00:00",
  "updatedAt": "2025-01-01T12:00:00"
}
```

### 6.5 사업장 권한 관리 API (ADMIN 전용)

#### POST /api/v1/business-places/{businessNumber}/admins
특정 관리자에게 사업장 권한 부여

**Request**:
```bash
curl -X POST http://localhost:8080/api/v1/business-places/1234567890/admins \
  -H "Content-Type: application/json" \
  -H "X-Admin-Id: 1" \
  -H "X-Admin-Role: ADMIN" \
  -d '{"adminId": 2}'
```

**Response (201 Created)**:
```json
{
  "businessNumber": "1234567890",
  "adminId": 2,
  "grantedAt": "2025-01-01T12:00:00"
}
```

#### GET /api/v1/business-places/{businessNumber}/admins
사업장의 권한 목록 조회

**Response (200 OK)**:
```json
{
  "businessNumber": "1234567890",
  "admins": [
    {
      "adminId": 2,
      "username": "manager1",
      "role": "MANAGER",
      "grantedAt": "2025-01-01T12:00:00"
    }
  ]
}
```

#### DELETE /api/v1/business-places/{businessNumber}/admins/{adminId}
권한 삭제

**Response (204 No Content)**

### 6.6 부가세 조회 API

#### GET /api/v1/vat?businessNumber={businessNumber}

**권한별 동작**:
- **ADMIN**: `businessNumber` 생략 시 전체 사업장 조회
- **MANAGER**: `businessNumber` 생략 시 권한 있는 사업장만 조회

**Request (ADMIN - 전체 조회)**:
```bash
curl http://localhost:8080/api/v1/vat \
  -H "X-Admin-Id: 1" \
  -H "X-Admin-Role: ADMIN"
```

**Request (MANAGER - 특정 사업장)**:
```bash
curl http://localhost:8080/api/v1/vat?businessNumber=1234567890 \
  -H "X-Admin-Id: 2" \
  -H "X-Admin-Role: MANAGER"
```

**Response (200 OK)**:
```json
[
  {
    "businessNumber": "1234567890",
    "businessName": "테스트 주식회사",
    "totalSales": 10000000,
    "totalPurchases": 5000000,
    "vatAmount": 454550,
    "calculatedAt": "2025-01-01T12:00:00"
  }
]
```

**부가세 계산 로직**:
```kotlin
// (매출 - 매입) × 1/11, 1의 자리 반올림
// 예시: (10,000,000 - 5,000,000) × 1/11 = 454,545.45...
//       → 454,545 (소수점 반올림)
//       → 454,550 (1의 자리 반올림, 10원 단위)

val taxBase = totalSales - totalPurchases
val vat = taxBase × (1/11)
val rounded = vat.setScale(0, HALF_UP)  // 소수점 반올림
val result = (rounded / 10).setScale(0, HALF_UP) * 10  // 10원 단위
```

**Error Cases**:
- `403 FORBIDDEN`: MANAGER가 권한 없는 사업장 조회 시
- `404 NOT_FOUND`: 존재하지 않는 사업장

---

## 7. 실행 방법

### 7.1 사전 요구사항
- JDK 17 이상 (권장: JDK 21 LTS)
- Gradle 8.x 이상
- Git

### 7.2 빌드 및 실행

#### 전체 빌드
```bash
./gradlew clean build
```

#### API 서버 실행 (포트 8080)
```bash
./gradlew :api-server:bootRun
```

#### Collector 실행 (별도 터미널, 포트 8081)
```bash
./gradlew :collector:bootRun
```

#### 테스트만 실행
```bash
./gradlew test
```

#### 빌드 스킵하고 테스트 제외
```bash
./gradlew clean build -x test
```

### 7.3 H2 Console 접속
- URL: http://localhost:8080/h2-console
- JDBC URL: `jdbc:h2:file:~/tax-data/taxdb;AUTO_SERVER=TRUE`
- Username: `sa`
- Password: (공백)

### 7.4 API 테스트 예시

#### 1. 수집 요청
```bash
curl -X POST http://localhost:8080/api/v1/collections \
  -H "Content-Type: application/json" \
  -H "X-Admin-Id: 1" \
  -H "X-Admin-Role: ADMIN" \
  -d '{"businessNumber": "1234567890"}'
```

#### 2. 상태 확인 (10초 대기 후)
```bash
curl http://localhost:8080/api/v1/collections/1234567890/status \
  -H "X-Admin-Id: 1" \
  -H "X-Admin-Role: ADMIN"
```

#### 3. 부가세 조회 (5분 후)
```bash
curl http://localhost:8080/api/v1/vat?businessNumber=1234567890 \
  -H "X-Admin-Id: 1" \
  -H "X-Admin-Role: ADMIN"
```

---

## 8. 주요 설계 결정사항

### 8.1 아키텍처 결정

#### Q1. 왜 멀티모듈 구조를 선택했는가?

**결정**: 4-모듈 아키텍처 (common, infrastructure, api-server, collector)

**이유**:
1. 과제 요구사항: "API 서버와 수집기" 명확히 구분
2. 관심사의 분리: API 처리와 데이터 수집 로직의 독립성
3. 배포 유연성: 각 모듈 독립적으로 스케일링 가능
4. 테스트 용이성: common 모듈은 프레임워크 없이 순수 단위 테스트 가능

**트레이드오프**:
- 단일 모듈보다 초기 설정 복잡
- 하지만 장기적 유지보수성과 확장성에서 이득

#### Q2. 왜 Database Polling 방식을 선택했는가?

**결정**: 10초 주기 Database Polling

**이유**:
1. 간단함: 별도 메시지 큐 인프라 불필요
2. 과제 범위: 5분 수집 시간 대비 10초 지연은 허용 가능
3. 상태 추적: DB를 단일 진실 공급원(Single Source of Truth)으로 사용

**대안 고려**:
- Kafka/RabbitMQ: 실시간성 향상하지만 과도한 인프라
- Spring @Async: 단일 JVM에서만 동작 (멀티모듈 취지와 불일치)

**향후 개선**: 트래픽 증가 시 메시지 큐 도입 고려

### 8.2 데이터베이스 설계 결정

#### Q3. 왜 사업자번호를 PK로 사용했는가?

**결정**: `business_number VARCHAR(10)` as Primary Key

**이유**:
1. 도메인 의미: 사업자번호는 불변하고 유일함 보장
2. 자연키: 별도 조인 없이 직관적 쿼리
3. 가독성: `WHERE business_number = '1234567890'` (vs `WHERE id = 123`)

**트레이드오프**:
- VARCHAR PK는 INT보다 약간 느림 (실용적으로 무시 가능)
- 하지만 도메인 표현력과 유지보수성에서 이득

#### Q4. 부가세 계산에서 왜 "1의 자리 반올림"인가?

**요구사항**: "1의 자리에서 반올림하여 처리 (Ex. 12345.12 -> 12350)"

**해석**:
1. 소수점 반올림: 12345.12 → 12345
2. **1의 자리 반올림**: 12345 → 1234.5 → 1235 → **12350** (10원 단위)

**구현**:
```kotlin
val vatRounded = vat.setScale(0, RoundingMode.HALF_UP)  // 소수점 제거
val result = vatRounded
    .divide(BigDecimal.TEN, 1, RoundingMode.HALF_UP)   // ÷10
    .setScale(0, RoundingMode.HALF_UP)                  // 1의 자리 반올림
    .multiply(BigDecimal.TEN)                           // ×10 → 10원 단위
```

**중요**: 실무에서는 국세청 부가세 계산 규정 확인 필요

### 8.3 보안 설계 결정

#### Q5. Header 기반 인증의 한계는?

**현재 구현**:
```
X-Admin-Id: 1
X-Admin-Role: ADMIN
```

**한계**:
- ❌ 누구나 헤더 위조 가능 (보안 취약)
- ❌ 세션 관리 없음
- ❌ 토큰 만료 개념 없음

**운영 환경 개선 방안**:
1. JWT 토큰 기반 인증
2. API Gateway에서 인증 처리
3. Refresh Token 도입
4. HTTPS 강제

**과제에서 허용된 이유**: "권한 체크는 header에 관리자 정보에 대한 값을 설정하여 체크해도 괜찮습니다."

### 8.4 비즈니스 로직 결정

#### Q6. 상태 전이를 도메인 메서드로 강제한 이유?

**설계**:
```kotlin
class BusinessPlace {
    fun startCollection() {
        require(collectionStatus == NOT_REQUESTED) {
            "수집은 NOT_REQUESTED 상태에서만 시작할 수 있습니다."
        }
        collectionStatus = COLLECTING
    }
}
```

**이유**:
1. 불변식 보호: 잘못된 상태 전이 방지
2. 도메인 로직 캡슐화: 비즈니스 규칙을 Entity에 명시
3. 가독성: `businessPlace.startCollection()` vs `businessPlace.collectionStatus = COLLECTING`

**효과**:
- ✅ `COLLECTED` → `COLLECTING` 전이 불가 (컴파일 타임 보장 X, 런타임 예외)
- ✅ 상태 관리 로직이 한 곳에 집중

#### Q7. sample 데이터는 어떻게 구성되어 있는가?

**파일 구조**: `sample.xlsx` (Excel 형식)
- **시트**: "매출" (412건), "매입" (42건)
- **컬럼**: 금액 | 날짜 (2개 컬럼, 헤더 없음)
- **데이터 예시**:
  ```
  매출 시트:
  147000    2025-08-01
  235500    2025-08-01
  383000    2025-08-01

  매입 시트:
  18400     2025-08-01
  38200     2025-08-03
  32800     2025-08-03
  ```

**데이터 통계**:
| 항목 | 건수 | 합계 |
|------|------|------|
| 매출 | 412건 | 47,811,032원 |
| 매입 | 42건 | 1,406,700원 |
| 예상 부가세 | - | 4,218,580원 |

**ExcelParser 구현**:
```kotlin
fun parseExcelFile(filePath: String, businessNumber: String): List<Transaction> {
    // 1. Apache POI로 Excel 파일 읽기
    // 2. "매출", "매입" 시트 파싱 (헤더 없이 첫 행부터)
    // 3. [금액, 날짜] 형식으로 데이터 추출
    // 4. 거래처명 자동 생성 (고객1, 공급사1 등)
    // 5. Transaction 엔티티로 변환하여 반환
}
```

**Collector 설정**:
```yaml
# collector/src/main/resources/application.yml
collector:
  data-file: sample.xlsx  # 수집할 엑셀 파일 경로
```

**설계 이유**:
1. 요구사항 충족: "sample 문서에 있는 매출/매입 탭에 있는 값을 DB에 적재"
2. 간결한 데이터 구조: 필수 정보(금액, 날짜)만 포함
3. 확장성: 파일 경로를 설정으로 외부화하여 다른 파일로 쉽게 교체 가능
4. 유연성: 거래처명은 파싱 시점에 자동 생성되어 파일 구조 단순화

---

## 9. 향후 개선 사항

### 9.1 기능 개선
- [ ] 수집 이력 관리 (성공/실패 로그)
- [ ] 재수집 정책 (일일 최대 횟수 제한)
- [ ] 수집 완료 알림 (이메일/Slack)
- [ ] 데이터 검증 (이상치 탐지)

### 9.2 성능 개선
- [ ] 부가세 계산 결과 캐싱 (Redis)
- [ ] 권한 정보 캐싱
- [ ] 페이지네이션 (부가세 조회)
- [ ] Batch Insert 활용

### 9.3 보안 강화
- [ ] JWT 기반 인증
- [ ] OAuth2/OIDC 통합
- [ ] 감사 로그 (모든 API 호출 기록)
- [ ] 민감 데이터 암호화

### 9.4 운영 개선
- [ ] Actuator + Prometheus 모니터링
- [ ] Grafana 대시보드
- [ ] ELK Stack 로깅
- [ ] Docker 컨테이너화
- [ ] CI/CD 파이프라인

### 9.5 아키텍처 개선
- [ ] Message Queue 도입 (Kafka/RabbitMQ)
- [ ] API Gateway 도입
- [ ] 서비스 분리 (Microservices)

---

## 부록

### A. 프로젝트 구조
```
tax/
├── settings.gradle.kts
├── build.gradle.kts
├── CLAUDE.md                    # Claude Code 가이드
├── README.md                    # 프로젝트 소개
├── project.md                   # 본 문서 (과제 구현 설명)
├── sample.xlsx                  # 수집 데이터 (Excel, 매출 412건/매입 42건)
│
├── common/                      # 순수 도메인 모듈
│   └── src/main/kotlin/com/kcd/tax/common/
│       ├── enums/               # CollectionStatus, AdminRole, TransactionType
│       └── exception/           # ErrorCode, BusinessException
│
├── infrastructure/              # 기술 인프라 모듈
│   └── src/main/kotlin/com/kcd/tax/infrastructure/
│       ├── domain/              # JPA Entity (BusinessPlace, Admin, Transaction)
│       ├── repository/          # JPA Repository interfaces
│       └── util/                # VatCalculator, ExcelParser
│
├── api-server/                  # REST API 서버
│   └── src/main/kotlin/com/kcd/tax/api/
│       ├── TaxApiApplication.kt
│       ├── controller/          # CollectionController, VatController, etc.
│       ├── service/             # CollectionService, VatCalculationService
│       ├── dto/                 # Request/Response DTOs
│       ├── security/            # AuthContext, AdminAuthInterceptor
│       ├── exception/           # GlobalExceptionHandler
│       └── config/              # WebConfig, JpaConfig
│
└── collector/                   # 데이터 수집기
    └── src/main/kotlin/com/kcd/tax/collector/
        ├── CollectorApplication.kt
        ├── service/             # CollectorService
        ├── scheduler/           # ScheduledCollectionPoller
        └── config/              # AsyncConfig, JpaConfig
```

### B. 참고 자료
- [CLAUDE.md](./CLAUDE.md) - 상세 개발 가이드 및 코드 예제
- [Spring Boot 공식 문서](https://spring.io/projects/spring-boot)
- [Kotlin 공식 문서](https://kotlinlang.org/docs/home.html)
- [Spring Data JPA](https://spring.io/projects/spring-data-jpa)

---

**문서 버전**: 2.0
**최종 수정일**: 2025-01-21
**작성 목적**: 세금 TF 개발 과제의 요구사항 분석 및 구현 설명
