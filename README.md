# 세금 TF 개발 과제

사업장의 매출/매입 데이터를 수집하고 부가세를 계산하는 멀티모듈 시스템

## 📋 프로젝트 개요

### 주요 기능

1. **데이터 수집 API** - 사업장의 매출/매입 데이터 수집 요청
2. **수집 상태 조회 API** - 수집 진행 상황 확인 (NOT_REQUESTED → COLLECTING → COLLECTED)
3. **사업장 관리 API** - 사업장 생성/조회/수정 (ADMIN 전용, CRUD 완전 구현)
4. **사업장 권한 관리 API** - ADMIN이 MANAGER에게 사업장 접근 권한 부여/조회/삭제
5. **부가세 조회 API** - 권한에 따라 사업장별 부가세 계산 결과 조회

### 기술 스택

- **Language**: Kotlin 1.9.25
- **Framework**: Spring Boot 3.5.7
- **ORM**: Spring Data JPA (Hibernate)
- **Database**: H2 (File-based with AUTO_SERVER)
- **Build Tool**: Gradle 8.14.3 (Kotlin DSL)
- **Java**: JDK 21 LTS

---

## 🏗 프로젝트 구조 (멀티모듈)

```
tax/
├── common/                      # 순수 도메인 모듈 (프레임워크 독립)
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
├── api-server/                  # REST API 서버 (포트 8080)
│   └── src/main/kotlin/com/kcd/tax/api/
│       ├── TaxApiApplication.kt
│       ├── controller/          # REST API 엔드포인트
│       ├── service/             # 비즈니스 로직
│       ├── dto/                 # Request/Response DTO
│       ├── security/            # 인증/인가 (Header 기반)
│       ├── exception/           # 예외 처리
│       └── config/              # 설정
│
└── collector/                   # 데이터 수집기 (포트 8081)
    └── src/main/kotlin/com/kcd/tax/collector/
        ├── CollectorApplication.kt
        ├── service/             # CollectorService (비동기 수집)
        ├── scheduler/           # ScheduledCollectionPoller (10초 폴링)
        └── config/              # 설정
```

### 모듈 간 의존성

```
api-server    →  infrastructure  →  common (SLF4J only)
collector     →  infrastructure  →  common
```

---

## 🚀 실행 방법

### 사전 요구사항
- JDK 17 이상 (권장: JDK 21 LTS)
- Gradle 8.x 이상

### 1. 전체 빌드

```bash
./gradlew clean build
```

### 2. 애플리케이션 실행

#### API 서버 실행 (포트 8080)
```bash
./gradlew :api-server:bootRun
```

#### Collector 실행 (별도 터미널, 포트 8081)
```bash
./gradlew :collector:bootRun
```

**중요**: API 서버와 Collector를 모두 실행해야 수집 기능이 정상 동작합니다.

### 3. 테스트만 실행

```bash
./gradlew test
```

### 4. 빌드 스킵 (테스트 제외)

```bash
./gradlew clean build -x test
```

---

## 💾 H2 Database Console

- **URL**: http://localhost:8080/h2-console
- **JDBC URL**: `jdbc:h2:file:~/tax-data/taxdb;AUTO_SERVER=TRUE`
- **Username**: `sa`
- **Password**: (공백)

**참고**: File-based H2 DB를 사용하며, AUTO_SERVER 모드로 API 서버와 Collector가 동시에 접근 가능합니다.

---

## 📊 샘플 데이터 파일

프로젝트 루트에 `sample.xlsx` 파일이 포함되어 있으며, 실제 데이터 수집 시 이 파일에서 매출/매입 데이터를 읽어옵니다.

### 파일 구조

- **시트**: "매출" (412건), "매입" (42건)
- **컬럼**: 금액 | 날짜 (2개 컬럼, 헤더 없음)
- **데이터 형식**:
  - 금액: 숫자 (예: 147000, 235500)
  - 날짜: 날짜 형식 (예: 2025-08-01)
- **거래처명**: 자동 생성 (고객1, 고객2... / 공급사1, 공급사2...)

### 데이터 통계

| 항목 | 건수 | 합계 |
|------|------|------|
| 매출 | 412건 | 47,811,032원 |
| 매입 | 42건 | 1,406,700원 |
| **예상 부가세** | - | **4,218,580원** |

### Collector 설정

`collector/src/main/resources/application.yml`에서 데이터 파일 경로를 설정할 수 있습니다:

```yaml
collector:
  data-file: sample.xlsx  # 수집할 엑셀 파일 경로
```

---

## 🔑 주요 설계 결정

### 1. 멀티모듈 아키텍처

**구성**: 4개 모듈 (common, infrastructure, api-server, collector)

**이유**:
- 과제 요구사항: "API 서버와 수집기로 구성"
- 관심사의 분리: API 처리와 데이터 수집 로직 독립
- 배포 유연성: 각 모듈 독립적으로 스케일링 가능

### 2. Database Polling 방식 통신

**API 서버**: 수집 요청 시 상태를 NOT_REQUESTED로 유지
**Collector**: 10초마다 DB 폴링하여 NOT_REQUESTED 상태의 사업장 자동 수집

```
Client → API Server → DB (상태: NOT_REQUESTED)
                       ↓ (10초 폴링)
                    Collector → 5분 수집 → DB (상태: COLLECTED)
```

### 3. 부가세 계산 로직

**공식**: `(매출 - 매입) × 1/11`
**반올림**: 1의 자리에서 반올림하여 10원 단위로 처리

**예시**:
```
(10,000,000 - 5,000,000) × 1/11 = 454,545.45...
→ 454,545 (소수점 반올림)
→ 454,550 (1의 자리 반올림하여 10원 단위)
```

**구현** (VatCalculator.kt):
```kotlin
val vat = taxBase.multiply(VAT_RATE)  // 1/11 계산
val vatRounded = vat.setScale(0, RoundingMode.HALF_UP)  // 소수점 반올림
val result = vatRounded
    .divide(BigDecimal.TEN, 1, RoundingMode.HALF_UP)  // 10으로 나눔
    .setScale(0, RoundingMode.HALF_UP)                 // 1의 자리 반올림
    .multiply(BigDecimal.TEN)                          // 10 곱해서 10원 단위
```

### 4. 권한 기반 접근 제어

- **Header 방식**: `X-Admin-Id`, `X-Admin-Role`
- **ADMIN**: 모든 사업장 조회 및 권한 관리 가능
- **MANAGER**: 할당된 사업장만 조회 가능

⚠️ **보안 주의**: 현재 Header 기반 인증은 프로토타입용입니다. 운영 환경에서는 JWT/OAuth2 필요.

---

## 📡 API 명세

### 공통 헤더

모든 API 요청 시 필요:
```
X-Admin-Id: {adminId}           # 관리자 ID
X-Admin-Role: {ADMIN|MANAGER}   # 관리자 역할
```

### 1. 수집 요청

```bash
POST /api/v1/collections
Content-Type: application/json
X-Admin-Id: 1
X-Admin-Role: ADMIN

{
  "businessNumber": "1234567890"
}
```

**응답 (200 OK)**:
```json
{
  "businessNumber": "1234567890",
  "status": "NOT_REQUESTED",
  "message": "수집 요청이 접수되었습니다. Collector가 처리 예정입니다.",
  "timestamp": "2025-01-21T12:00:00"
}
```

**참고**: 수집 요청 직후에는 `NOT_REQUESTED` 상태입니다. Collector가 10초마다 폴링하여 `COLLECTING`으로 변경 후 5분간 수집합니다.

### 2. 수집 상태 조회

```bash
GET /api/v1/collections/{businessNumber}/status
X-Admin-Id: 1
X-Admin-Role: ADMIN
```

**응답 (200 OK)**:
```json
{
  "businessNumber": "1234567890",
  "status": "COLLECTED",
  "timestamp": "2025-01-21T12:05:00"
}
```

**상태 값**:
- `NOT_REQUESTED`: 수집 대기 중
- `COLLECTING`: 수집 진행 중 (최대 5분)
- `COLLECTED`: 수집 완료

### 3. 권한 부여 (ADMIN 전용)

```bash
POST /api/v1/business-places/{businessNumber}/admins
Content-Type: application/json
X-Admin-Id: 1
X-Admin-Role: ADMIN

{
  "adminId": 2
}
```

**응답 (201 Created)**:
```json
{
  "id": 1,
  "businessNumber": "1234567890",
  "adminId": 2,
  "adminUsername": "manager1",
  "adminRole": "MANAGER",
  "grantedAt": "2025-01-21T12:00:00"
}
```

### 4. 권한 목록 조회 (ADMIN 전용)

```bash
GET /api/v1/business-places/{businessNumber}/admins
X-Admin-Id: 1
X-Admin-Role: ADMIN
```

**응답 (200 OK)**:
```json
{
  "businessNumber": "1234567890",
  "admins": [
    {
      "id": 1,
      "businessNumber": "1234567890",
      "adminId": 2,
      "adminUsername": "manager1",
      "adminRole": "MANAGER",
      "grantedAt": "2025-01-21T12:00:00"
    }
  ]
}
```

### 5. 권한 삭제 (ADMIN 전용)

```bash
DELETE /api/v1/business-places/{businessNumber}/admins/{adminId}
X-Admin-Id: 1
X-Admin-Role: ADMIN
```

**응답**: 204 No Content

### 6. 부가세 조회

**전체 조회 (ADMIN)**:
```bash
GET /api/v1/vat
X-Admin-Id: 1
X-Admin-Role: ADMIN
```

**할당된 사업장만 조회 (MANAGER)**:
```bash
GET /api/v1/vat
X-Admin-Id: 2
X-Admin-Role: MANAGER
```

**특정 사업장 조회**:
```bash
GET /api/v1/vat?businessNumber=1234567890
X-Admin-Id: 2
X-Admin-Role: MANAGER
```

**응답 (200 OK)**:
```json
[
  {
    "businessNumber": "1234567890",
    "businessName": "테스트 주식회사",
    "totalSales": 10000000,
    "totalPurchases": 5000000,
    "vatAmount": 454550,
    "calculatedAt": "2025-01-21T12:00:00"
  }
]
```

---

## 🧪 테스트 시나리오

### 시나리오 1: 수집 및 부가세 조회 (ADMIN)

```bash
# 1. 수집 요청
curl -X POST http://localhost:8080/api/v1/collections \
  -H "Content-Type: application/json" \
  -H "X-Admin-Id: 1" \
  -H "X-Admin-Role: ADMIN" \
  -d '{"businessNumber": "1234567890"}'

# 2. 10초 후 상태 확인 (COLLECTING으로 변경됨)
curl http://localhost:8080/api/v1/collections/1234567890/status \
  -H "X-Admin-Id: 1" \
  -H "X-Admin-Role: ADMIN"

# 3. 5분 대기 후 상태 확인 (COLLECTED)
curl http://localhost:8080/api/v1/collections/1234567890/status \
  -H "X-Admin-Id: 1" \
  -H "X-Admin-Role: ADMIN"

# 4. 부가세 조회
curl http://localhost:8080/api/v1/vat?businessNumber=1234567890 \
  -H "X-Admin-Id: 1" \
  -H "X-Admin-Role: ADMIN"
```

### 시나리오 2: 권한 부여 및 조회 (ADMIN → MANAGER)

```bash
# 1. 권한 부여
curl -X POST http://localhost:8080/api/v1/business-places/1111111111/admins \
  -H "Content-Type: application/json" \
  -H "X-Admin-Id: 1" \
  -H "X-Admin-Role: ADMIN" \
  -d '{"adminId": 2}'

# 2. 권한 목록 확인
curl http://localhost:8080/api/v1/business-places/1111111111/admins \
  -H "X-Admin-Id: 1" \
  -H "X-Admin-Role: ADMIN"

# 3. MANAGER로 부가세 조회 (이제 가능)
curl http://localhost:8080/api/v1/vat?businessNumber=1111111111 \
  -H "X-Admin-Id: 2" \
  -H "X-Admin-Role: MANAGER"
```

---

## 📊 초기 데이터

### 관리자

| ID | Username | Role |
|----|----------|------|
| 1 | admin1 | ADMIN |
| 2 | manager1 | MANAGER |
| 3 | manager2 | MANAGER |

### 사업장

| 사업자번호 | 상호명 | 수집 상태 |
|-----------|--------|----------|
| 1234567890 | 테스트 주식회사 | NOT_REQUESTED |
| 0987654321 | 샘플 상사 | NOT_REQUESTED |
| 1111111111 | 데모 기업 | NOT_REQUESTED |

### 권한 매핑

- manager1(ID=2): 1234567890, 0987654321 접근 가능
- manager2(ID=3): 0987654321 접근 가능

---

## ⚠️ 주의사항

### 보안

⚠️ **현재 인증 방식은 프로토타입용입니다**

- Header 기반 인증 (`X-Admin-Id`, `X-Admin-Role`)은 쉽게 위조 가능
- 실제 프로덕션 환경에서는 JWT 또는 OAuth2 기반 인증 필요
- API Key 관리 및 암호화 고려 필요

### 비동기 수집

- **수집 시간**: 5분 소요
- **폴링 주기**: Collector가 10초마다 DB 확인
- **동시 수집**: 여러 사업장 동시 수집 가능 (ThreadPool 크기: 5)
- **수집 중 재요청**: 불가 (409 Conflict 반환)
- **실패 처리**: 자동으로 상태가 NOT_REQUESTED로 복원

### 데이터베이스

- **H2 파일 위치**: `~/tax-data/taxdb.mv.db`
- **AUTO_SERVER 모드**: API 서버와 Collector가 동시 접근 가능
- **재시작 시**: 데이터 유지됨 (file-based)
- **초기화**: `ddl-auto: create-drop` 설정으로 재시작 시 스키마 재생성

---

## 🔧 에러 코드

| 코드 | 메시지 | HTTP Status |
|------|--------|-------------|
| AUTH001 | 인증 정보가 없습니다 | 401 |
| AUTH003 | 권한이 없습니다 | 403 |
| BIZ001 | 사업장을 찾을 수 없습니다 | 404 |
| COL001 | 이미 수집이 진행 중입니다 | 409 |
| PER001 | 이미 권한이 부여되었습니다 | 409 |
| PER003 | 해당 사업장에 대한 권한이 없습니다 | 403 |

---

## 🚀 향후 개선 사항

### 기능
- [ ] 수집 이력 관리 (성공/실패 로그)
- [ ] 재수집 정책 (일일 최대 횟수 제한)
- [ ] 수집 완료 알림 (이메일/Slack)

### 성능
- [ ] 부가세 계산 결과 캐싱 (Redis)
- [ ] 권한 정보 캐싱
- [ ] 페이지네이션 (부가세 조회)

### 보안
- [ ] JWT 기반 인증
- [ ] OAuth2/OIDC 통합
- [ ] 감사 로그 (모든 API 호출 기록)

### 인프라
- [ ] Message Queue 도입 (Kafka/RabbitMQ) - Database Polling 대체
- [ ] Actuator + Prometheus + Grafana 모니터링
- [ ] Docker 컨테이너화
- [ ] CI/CD 파이프라인

---

## 📚 참고 문서

- **[CLAUDE.md](./CLAUDE.md)** - 상세 개발 가이드 (아키텍처, 코드 예제)
- **[project.md](./project.md)** - 과제 요구사항 분석 및 설계 설명

---

## 📄 라이센스

This project is for evaluation purposes only.
