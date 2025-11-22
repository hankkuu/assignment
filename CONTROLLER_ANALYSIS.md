# 컨트롤러 구조 분석 및 권한 설계

## 1. 현재 컨트롤러 구조

### 1.1 컨트롤러 목록

| 컨트롤러 | 경로 | 책임 | 분리 필요성 |
|---------|------|------|-----------|
| **HealthController** | `/health` | 헬스 체크 | ❌ 불필요 (단일 엔드포인트) |
| **CollectionController** | `/api/v1/collections` | 데이터 수집 요청/상태 조회 | ❌ 불필요 (관심사가 명확) |
| **VatController** | `/api/v1/vat` | 부가세 조회 | ❌ 불필요 (단일 엔드포인트) |
| **BusinessPlaceController** | `/api/v1/business-places` | 사업장 CRUD | ✅ **분리 완료** |
| **BusinessPlaceAdminController** | `/api/v1/business-places/{id}/admins` | 사업장 권한 관리 | ✅ **분리 완료** |

### 1.2 분리 완료 사항

#### BusinessPlaceController 분리 (✅ 완료)

**분리 전 (단일 컨트롤러)**
```
BusinessPlaceController
├── POST   /business-places              # 사업장 생성 (ADMIN)
├── GET    /business-places              # 사업장 목록 (ADMIN)
├── GET    /business-places/{id}         # 사업장 상세 (AUTH)
├── PUT    /business-places/{id}         # 사업장 수정 (ADMIN)
├── POST   /business-places/{id}/admins  # 권한 부여 (ADMIN)
├── GET    /business-places/{id}/admins  # 권한 목록 (ADMIN)
└── DELETE /business-places/{id}/admins/{adminId}  # 권한 해제 (ADMIN)
```

**분리 후 (두 개의 컨트롤러)**

**BusinessPlaceController** - 사업장 CRUD 전담
```
├── POST   /business-places              # 사업장 생성 (ADMIN)
├── GET    /business-places              # 사업장 목록 (ADMIN)
├── GET    /business-places/{id}         # 사업장 상세 (AUTH)
└── PUT    /business-places/{id}         # 사업장 수정 (ADMIN)
```

**BusinessPlaceAdminController** - 권한 관리 전담
```
├── POST   /business-places/{id}/admins           # 권한 부여 (ADMIN)
├── GET    /business-places/{id}/admins           # 권한 목록 (ADMIN)
└── DELETE /business-places/{id}/admins/{adminId} # 권한 해제 (ADMIN)
```

### 1.3 분리 이유

#### ✅ 분리가 적절한 경우: BusinessPlace

1. **Single Responsibility Principle (SRP) 위반**
   - 사업장 CRUD와 권한 관리는 **서로 다른 비즈니스 관심사**
   - 사업장 = "비즈니스 엔티티 관리"
   - 권한 = "접근 제어 관리"

2. **RESTful 설계 원칙**
   - `/business-places/{id}/admins`는 서브 리소스 패턴
   - 서브 리소스는 별도 컨트롤러로 분리하는 것이 REST 모범 사례

3. **확장성 고려**
   - 권한 관리 기능 추가 시 (예: 권한 수정, 권한 히스토리, 승인 워크플로우)
   - 사업장 CRUD 변경 없이 독립적으로 확장 가능

4. **테스트 용이성**
   - 각 컨트롤러의 테스트가 독립적으로 수행 가능
   - 권한 관련 테스트와 CRUD 테스트 분리

#### ❌ 분리가 불필요한 경우

**CollectionController**
- 수집 요청과 상태 조회는 **동일한 비즈니스 도메인**
- 엔드포인트가 2개로 단순함
- 추가 기능 확장 가능성 낮음

**VatController**
- 단일 조회 엔드포인트만 존재
- 비즈니스 로직이 명확하고 단순함

## 2. 권한 설계 분석

### 2.1 권한 레벨

| 레벨 | 설명 | 어노테이션 |
|-----|------|----------|
| **Public** | 인증 불필요 | (없음) |
| **Authenticated** | 로그인 필요 (ADMIN + MANAGER) | `@RequireAuth` |
| **Admin Only** | ADMIN 권한 필요 | `@RequireAuth` + `@RequireAdmin` |

### 2.2 API별 권한 매트릭스

| API | 경로 | 메서드 | 권한 | ADMIN | MANAGER | 이유 |
|-----|------|--------|------|-------|---------|------|
| **헬스 체크** | `/health` | GET | Public | ✅ | ✅ | 모니터링 시스템에서 사용 |
| **부가세 조회** | `/api/v1/vat` | GET | Auth | ✅ (전체) | ✅ (할당된 사업장만) | 권한별 데이터 필터링 |
| **수집 요청** | `/api/v1/collections` | POST | Auth | ✅ | ✅ | 데이터 수집은 관리자 모두 가능 |
| **수집 상태** | `/api/v1/collections/{id}/status` | GET | Auth | ✅ | ✅ | 상태 조회는 관리자 모두 가능 |
| **사업장 생성** | `/api/v1/business-places` | POST | **Admin** | ✅ | ❌ | 마스터 데이터 생성 권한 |
| **사업장 목록** | `/api/v1/business-places` | GET | **Admin** | ✅ | ❌ | 전체 사업장 조회는 ADMIN만 |
| **사업장 상세** | `/api/v1/business-places/{id}` | GET | Auth | ✅ | ✅ | 서비스에서 권한 체크 |
| **사업장 수정** | `/api/v1/business-places/{id}` | PUT | **Admin** | ✅ | ❌ | 마스터 데이터 수정 권한 |
| **권한 부여** | `/api/v1/business-places/{id}/admins` | POST | **Admin** | ✅ | ❌ | 권한 관리는 ADMIN만 |
| **권한 목록** | `/api/v1/business-places/{id}/admins` | GET | **Admin** | ✅ | ❌ | 권한 정보 조회는 ADMIN만 |
| **권한 해제** | `/api/v1/business-places/{id}/admins/{adminId}` | DELETE | **Admin** | ✅ | ❌ | 권한 관리는 ADMIN만 |

### 2.3 권한 분리가 필요한 이유

#### 2.3.1 역할 기반 접근 제어 (RBAC)

**ADMIN (관리자)**
- **권한 범위**: 모든 사업장에 대한 전체 권한
- **허용 작업**:
  - ✅ 사업장 CRUD (생성, 조회, 수정)
  - ✅ 권한 관리 (부여, 해제, 조회)
  - ✅ 데이터 수집 요청/상태 조회
  - ✅ 모든 사업장의 부가세 조회
- **사용 케이스**: 시스템 관리자, 슈퍼유저

**MANAGER (매니저)**
- **권한 범위**: 할당된 사업장에 대한 제한적 권한
- **허용 작업**:
  - ✅ 할당된 사업장의 데이터 수집 요청/상태 조회
  - ✅ 할당된 사업장의 부가세 조회
  - ✅ 할당된 사업장의 상세 정보 조회
  - ❌ 사업장 생성/수정 불가
  - ❌ 권한 관리 불가
  - ❌ 전체 사업장 목록 조회 불가
- **사용 케이스**: 특정 사업장 담당자, 부서별 관리자

#### 2.3.2 보안 원칙

**1. 최소 권한 원칙 (Principle of Least Privilege)**
```
MANAGER는 본인의 업무 수행에 필요한 최소한의 권한만 부여
→ 다른 사업장의 데이터 접근 불가
→ 권한 관리 기능 접근 불가
```

**2. 직무 분리 (Separation of Duties)**
```
권한 관리 = ADMIN만 가능
→ MANAGER가 스스로 권한을 확대할 수 없음
→ 내부 부정 방지
```

**3. 방어 계층화 (Defense in Depth)**
```
Controller 레벨: @RequireAdmin 어노테이션
    ↓
Service 레벨: checkPermission() 메서드로 2차 검증
    ↓
Repository 레벨: 권한 테이블 조인 쿼리
```

#### 2.3.3 권한 분리의 구체적 이유

**1. 사업장 생성/수정이 ADMIN만 가능한 이유**
```
이유:
- 사업장은 마스터 데이터 (Master Data)
- 잘못된 생성/수정 시 시스템 전체에 영향
- 세무 데이터의 정확성과 직결

위험 시나리오:
- MANAGER가 임의로 사업장 생성 → 가짜 사업장 데이터 증가
- MANAGER가 타 사업장 수정 → 데이터 무결성 위반
```

**2. 사업장 목록 조회가 ADMIN만 가능한 이유**
```
이유:
- 전체 사업장 정보는 민감한 비즈니스 정보
- MANAGER는 할당된 사업장만 알아야 함 (Need-to-Know)

위험 시나리오:
- MANAGER가 전체 목록 조회 → 경쟁사 정보 유출 가능
- MANAGER가 타 담당자의 사업장 파악 → 내부 정보 유출
```

**3. 권한 관리가 ADMIN만 가능한 이유**
```
이유:
- 권한 상승 공격(Privilege Escalation) 방지
- 감사 추적(Audit Trail) 명확성

위험 시나리오:
- MANAGER가 본인에게 다른 사업장 권한 부여
- MANAGER가 타 MANAGER의 권한 해제
- 권한 관리 이력 추적 불가능
```

**4. 부가세 조회는 AUTH(모두)가 가능하지만 서비스에서 필터링하는 이유**
```
이유:
- 유연한 권한 제어 (컨트롤러는 단순, 서비스는 복잡)
- 비즈니스 로직에서 동적 필터링

구현:
VatCalculationService.getAuthorizedBusinessNumbers(adminId, role)
→ ADMIN: 모든 사업장 반환
→ MANAGER: businessPlaceAdminRepository에서 할당된 사업장만 반환
```

## 3. 추가 분리 검토가 필요한 경우

### 3.1 미래 확장 시나리오

다음과 같은 기능이 추가될 경우 **추가 분리를 고려**해야 합니다:

#### Scenario 1: 사업장 통계/리포트 기능 추가
```
현재: BusinessPlaceController (CRUD만)
추가: BusinessPlaceReportController
      ├── GET /business-places/statistics
      ├── GET /business-places/analytics
      └── GET /business-places/{id}/report
```
**분리 이유**: 조회(CRUD)와 분석(Report)은 다른 관심사

#### Scenario 2: 수집 스케줄링 기능 추가
```
현재: CollectionController (요청/상태만)
추가: CollectionScheduleController
      ├── POST   /collections/schedules      # 스케줄 생성
      ├── GET    /collections/schedules      # 스케줄 목록
      ├── PUT    /collections/schedules/{id} # 스케줄 수정
      └── DELETE /collections/schedules/{id} # 스케줄 삭제
```
**분리 이유**: 즉시 수집과 스케줄 관리는 다른 관심사

#### Scenario 3: 관리자 자체 CRUD 추가
```
현재: (관리자 CRUD 없음, 권한 부여만 존재)
추가: AdminController
      ├── POST   /admins           # 관리자 생성
      ├── GET    /admins           # 관리자 목록
      ├── GET    /admins/{id}      # 관리자 상세
      ├── PUT    /admins/{id}      # 관리자 수정
      └── DELETE /admins/{id}      # 관리자 삭제
```
**분리 이유**: 관리자 CRUD와 사업장-관리자 매핑은 다른 도메인

## 4. 권한 설계 베스트 프랙티스

### 4.1 현재 구현의 장점

✅ **컨트롤러 레벨 검증**
```kotlin
@RequireAuth  // 1차: 인증 체크
@RequireAdmin // 2차: 권한 체크
```

✅ **서비스 레벨 검증**
```kotlin
// VatCalculationService.kt
fun checkPermission(businessNumber: String, adminId: Long, role: AdminRole) {
    if (role == AdminRole.ADMIN) return

    val hasPermission = businessPlaceAdminRepository
        .existsByBusinessNumberAndAdminId(businessNumber, adminId)

    if (!hasPermission) {
        throw ForbiddenException("해당 사업장에 대한 접근 권한이 없습니다")
    }
}
```

✅ **데이터베이스 레벨 검증**
```sql
-- business_place_admin 테이블로 N:M 관계 관리
SELECT bp.*
FROM business_place bp
JOIN business_place_admin bpa ON bp.business_number = bpa.business_number
WHERE bpa.admin_id = :adminId
```

### 4.2 개선 제안

#### 제안 1: 권한 검증 일관성
```kotlin
// 현재
@GetMapping("/{businessNumber}")
fun getBusinessPlace(@PathVariable businessNumber: String) {
    // 서비스에서 권한 체크
}

// 개선안: 통일된 권한 체크 방식
@GetMapping("/{businessNumber}")
@RequireAuth
@CheckBusinessPermission  // 커스텀 어노테이션
fun getBusinessPlace(@PathVariable businessNumber: String) {
    // AOP로 권한 체크 자동화
}
```

#### 제안 2: 권한 로깅 및 감사
```kotlin
// 권한 민감 작업은 감사 로그 기록
@PostMapping
@RequireAdmin
@AuditLog(action = "GRANT_PERMISSION")  // 커스텀 어노테이션
fun grantPermission(...) {
    // 누가, 언제, 어떤 권한을 부여했는지 기록
}
```

#### 제안 3: 역할 확장 대비
```kotlin
// 현재: ADMIN, MANAGER 2개
// 미래: SUPER_ADMIN, ADMIN, MANAGER, VIEWER 등 추가 가능

enum class AdminRole {
    SUPER_ADMIN,  // 모든 권한 + 시스템 설정
    ADMIN,        // 현재 ADMIN 권한
    MANAGER,      // 현재 MANAGER 권한
    VIEWER        // 조회만 가능
}
```

## 5. 결론

### 5.1 컨트롤러 분리 현황

✅ **적절하게 분리됨**: BusinessPlaceController ↔ BusinessPlaceAdminController
❌ **추가 분리 불필요**: CollectionController, VatController, HealthController

### 5.2 권한 분리의 핵심 원칙

1. **ADMIN**: 시스템 전체 관리 → 마스터 데이터 CRUD + 권한 관리
2. **MANAGER**: 할당된 사업장만 관리 → 조회 + 데이터 수집만
3. **보안 3계층**: Controller 어노테이션 → Service 메서드 → DB 쿼리

### 5.3 권한 분리 이유 요약

| 작업 | ADMIN | MANAGER | 이유 |
|-----|-------|---------|------|
| 사업장 생성/수정 | ✅ | ❌ | 마스터 데이터 보호 |
| 전체 목록 조회 | ✅ | ❌ | 정보 유출 방지 |
| 권한 관리 | ✅ | ❌ | 권한 상승 공격 방지 |
| 할당 사업장 조회 | ✅ | ✅ | 업무 수행 필요 |
| 부가세 계산 | ✅ | ✅ | 업무 수행 필요 (필터링) |
| 데이터 수집 | ✅ | ✅ | 업무 수행 필요 |

이 설계는 **최소 권한 원칙**, **직무 분리**, **방어 계층화**를 모두 충족합니다.
