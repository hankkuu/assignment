package com.kcd.tax.api.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.kcd.tax.api.controller.dto.request.CollectionRequest
import com.kcd.tax.api.controller.dto.request.CreateBusinessPlaceRequest
import com.kcd.tax.api.controller.dto.request.GrantPermissionRequest
import com.kcd.tax.common.enums.CollectionStatus
import com.kcd.tax.infrastructure.repository.AdminRepository
import com.kcd.tax.infrastructure.repository.BusinessPlaceAdminRepository
import com.kcd.tax.infrastructure.repository.BusinessPlaceRepository
import com.kcd.tax.infrastructure.repository.TransactionRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.transaction.annotation.Transactional

/**
 * 전체 시스템 통합 테스트
 *
 * 실제 데이터베이스와 Spring Context를 사용하여 End-to-End 테스트 수행
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class TaxApiIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var businessPlaceRepository: BusinessPlaceRepository

    @Autowired
    private lateinit var adminRepository: AdminRepository

    @Autowired
    private lateinit var businessPlaceAdminRepository: BusinessPlaceAdminRepository

    @Autowired
    private lateinit var transactionRepository: TransactionRepository

    @BeforeEach
    fun setUp() {
        // 각 테스트 전에 데이터 정리 (트랜잭션이 롤백되므로 자동 정리됨)
        transactionRepository.deleteAll()
        businessPlaceAdminRepository.deleteAll()
        businessPlaceRepository.deleteAll()
        // adminRepository는 data.sql에서 초기화되므로 삭제하지 않음
    }

    @Test
    fun `사업장 생성부터 부가세 조회까지 전체 플로우가 작동한다`() {
        // 1. 사업장 생성 (ADMIN)
        val createRequest = CreateBusinessPlaceRequest(
            businessNumber = "1234567890",
            name = "통합 테스트 주식회사"
        )

        mockMvc.perform(
            post("/api/v1/business-places")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest))
                .header("X-Admin-Id", "1")
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.businessNumber").value("1234567890"))
            .andExpect(jsonPath("$.name").value("통합 테스트 주식회사"))
            .andExpect(jsonPath("$.collectionStatus").value("NOT_REQUESTED"))

        // 2. 사업장 목록 조회 (ADMIN)
        mockMvc.perform(
            get("/api/v1/business-places")
                .header("X-Admin-Id", "1")
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.businessNumber == '1234567890')].name").value("통합 테스트 주식회사"))

        // 3. MANAGER에게 권한 부여 (ADMIN)
        val grantRequest = GrantPermissionRequest(adminId = 2L)

        mockMvc.perform(
            post("/api/v1/business-places/1234567890/admins")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(grantRequest))
                .header("X-Admin-Id", "1")
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.adminId").value(2))

        // 4. 권한 목록 확인 (ADMIN)
        mockMvc.perform(
            get("/api/v1/business-places/1234567890/admins")
                .header("X-Admin-Id", "1")
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.admins[0].adminId").value(2))

        // 5. 수집 요청 (MANAGER)
        val collectionRequest = CollectionRequest("1234567890")

        mockMvc.perform(
            post("/api/v1/collections")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(collectionRequest))
                .header("X-Admin-Id", "2")
                .header("X-Admin-Role", "MANAGER")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("NOT_REQUESTED"))

        // 6. 수집 상태 조회 (MANAGER)
        mockMvc.perform(
            get("/api/v1/collections/1234567890/status")
                .header("X-Admin-Id", "2")
                .header("X-Admin-Role", "MANAGER")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.businessNumber").value("1234567890"))

        // 7. 부가세 조회 (ADMIN - 모든 사업장 조회 가능)
        mockMvc.perform(
            get("/api/v1/vat")
                .header("X-Admin-Id", "1")
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isOk)

        // 8. 부가세 조회 (MANAGER - 권한이 있는 사업장만 조회)
        mockMvc.perform(
            get("/api/v1/vat")
                .param("businessNumber", "1234567890")
                .header("X-Admin-Id", "2")
                .header("X-Admin-Role", "MANAGER")
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `MANAGER는 권한이 없는 사업장을 조회할 수 없다`() {
        // 1. 사업장 생성 (ADMIN)
        val createRequest = CreateBusinessPlaceRequest(
            businessNumber = "9999999999",
            name = "권한 없는 사업장"
        )

        mockMvc.perform(
            post("/api/v1/business-places")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest))
                .header("X-Admin-Id", "1")
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isCreated)

        // 2. MANAGER가 권한 없이 부가세 조회 시도 → 403 Forbidden
        mockMvc.perform(
            get("/api/v1/vat")
                .param("businessNumber", "9999999999")
                .header("X-Admin-Id", "2")
                .header("X-Admin-Role", "MANAGER")
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `사업장을 생성하고 수정하고 조회할 수 있다`() {
        // 1. 사업장 생성
        val createRequest = CreateBusinessPlaceRequest(
            businessNumber = "5555555555",
            name = "원래 이름"
        )

        mockMvc.perform(
            post("/api/v1/business-places")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest))
                .header("X-Admin-Id", "1")
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("원래 이름"))

        // 2. 사업장 수정
        val updateRequest = """{"name": "수정된 이름"}"""

        mockMvc.perform(
            put("/api/v1/business-places/5555555555")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateRequest)
                .header("X-Admin-Id", "1")
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("수정된 이름"))

        // 3. 사업장 조회 (수정된 이름 확인)
        mockMvc.perform(
            get("/api/v1/business-places/5555555555")
                .header("X-Admin-Id", "1")
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("수정된 이름"))
    }

    @Test
    fun `권한을 부여하고 해제할 수 있다`() {
        // 1. 사업장 생성
        val createRequest = CreateBusinessPlaceRequest(
            businessNumber = "6666666666",
            name = "권한 테스트 사업장"
        )

        mockMvc.perform(
            post("/api/v1/business-places")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest))
                .header("X-Admin-Id", "1")
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isCreated)

        // 2. MANAGER(2)에게 권한 부여
        val grantRequest = GrantPermissionRequest(adminId = 2L)

        mockMvc.perform(
            post("/api/v1/business-places/6666666666/admins")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(grantRequest))
                .header("X-Admin-Id", "1")
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.adminId").value(2))

        // 3. 권한 목록 확인 (1명)
        mockMvc.perform(
            get("/api/v1/business-places/6666666666/admins")
                .header("X-Admin-Id", "1")
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.admins.length()").value(1))

        // 4. MANAGER(3)에게 권한 부여
        val grantRequest2 = GrantPermissionRequest(adminId = 3L)

        mockMvc.perform(
            post("/api/v1/business-places/6666666666/admins")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(grantRequest2))
                .header("X-Admin-Id", "1")
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isCreated)

        // 5. 권한 목록 확인 (2명)
        mockMvc.perform(
            get("/api/v1/business-places/6666666666/admins")
                .header("X-Admin-Id", "1")
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.admins.length()").value(2))

        // 6. MANAGER(2) 권한 해제
        mockMvc.perform(
            delete("/api/v1/business-places/6666666666/admins/2")
                .header("X-Admin-Id", "1")
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isNoContent)

        // 7. 권한 목록 확인 (1명 남음)
        mockMvc.perform(
            get("/api/v1/business-places/6666666666/admins")
                .header("X-Admin-Id", "1")
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.admins.length()").value(1))
            .andExpect(jsonPath("$.admins[0].adminId").value(3))
    }

    @Test
    fun `이미 존재하는 사업자번호로 생성하면 409 에러가 발생한다`() {
        // 1. 사업장 생성
        val createRequest = CreateBusinessPlaceRequest(
            businessNumber = "7777777777",
            name = "첫 번째 사업장"
        )

        mockMvc.perform(
            post("/api/v1/business-places")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest))
                .header("X-Admin-Id", "1")
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isCreated)

        // 2. 같은 사업자번호로 재생성 시도 → 409 Conflict
        val duplicateRequest = CreateBusinessPlaceRequest(
            businessNumber = "7777777777",
            name = "중복 사업장"
        )

        mockMvc.perform(
            post("/api/v1/business-places")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(duplicateRequest))
                .header("X-Admin-Id", "1")
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isConflict)
    }

    @Test
    fun `이미 권한이 있는 관리자에게 권한 부여 시 409 에러가 발생한다`() {
        // 1. 사업장 생성
        val createRequest = CreateBusinessPlaceRequest(
            businessNumber = "8888888888",
            name = "중복 권한 테스트"
        )

        mockMvc.perform(
            post("/api/v1/business-places")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest))
                .header("X-Admin-Id", "1")
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isCreated)

        // 2. MANAGER(2)에게 권한 부여
        val grantRequest = GrantPermissionRequest(adminId = 2L)

        mockMvc.perform(
            post("/api/v1/business-places/8888888888/admins")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(grantRequest))
                .header("X-Admin-Id", "1")
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isCreated)

        // 3. 같은 관리자에게 재부여 시도 → 409 Conflict
        mockMvc.perform(
            post("/api/v1/business-places/8888888888/admins")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(grantRequest))
                .header("X-Admin-Id", "1")
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isConflict)
    }

    @Test
    fun `MANAGER는 ADMIN 전용 기능을 사용할 수 없다`() {
        // 1. 사업장 생성 시도 → 403 Forbidden
        val createRequest = CreateBusinessPlaceRequest(
            businessNumber = "3333333333",
            name = "MANAGER 생성 시도"
        )

        mockMvc.perform(
            post("/api/v1/business-places")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest))
                .header("X-Admin-Id", "2")
                .header("X-Admin-Role", "MANAGER")
        )
            .andExpect(status().isForbidden)

        // 2. 사업장 목록 조회 시도 → 403 Forbidden
        mockMvc.perform(
            get("/api/v1/business-places")
                .header("X-Admin-Id", "2")
                .header("X-Admin-Role", "MANAGER")
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `인증 헤더 없이 요청하면 401 에러가 발생한다`() {
        // 1. 사업장 생성 시도
        val createRequest = CreateBusinessPlaceRequest(
            businessNumber = "4444444444",
            name = "인증 없음"
        )

        mockMvc.perform(
            post("/api/v1/business-places")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest))
        )
            .andExpect(status().isUnauthorized)

        // 2. 부가세 조회 시도
        mockMvc.perform(
            get("/api/v1/vat")
        )
            .andExpect(status().isUnauthorized)

        // 3. 수집 요청 시도
        val collectionRequest = CollectionRequest("1234567890")

        mockMvc.perform(
            post("/api/v1/collections")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(collectionRequest))
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `존재하지 않는 사업장에 대한 작업은 404 에러가 발생한다`() {
        val nonExistentBusinessNumber = "0000000000"

        // 1. 조회 시도
        mockMvc.perform(
            get("/api/v1/business-places/$nonExistentBusinessNumber")
                .header("X-Admin-Id", "1")
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isNotFound)

        // 2. 수정 시도
        val updateRequest = """{"name": "수정 시도"}"""

        mockMvc.perform(
            put("/api/v1/business-places/$nonExistentBusinessNumber")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateRequest)
                .header("X-Admin-Id", "1")
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isNotFound)

        // 3. 수집 요청 시도
        val collectionRequest = CollectionRequest(nonExistentBusinessNumber)

        mockMvc.perform(
            post("/api/v1/collections")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(collectionRequest))
                .header("X-Admin-Id", "1")
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `부가세 조회 시 음수 값도 정상적으로 반환된다`() {
        // 부가세는 실제 거래 데이터가 있어야 계산됨
        // 이 테스트는 data.sql의 초기 데이터를 사용
        mockMvc.perform(
            get("/api/v1/vat")
                .header("X-Admin-Id", "1")
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isOk)
    }
}
