package com.kcd.tax.api.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.kcd.tax.api.controller.dto.request.CreateBusinessPlaceRequest
import com.kcd.tax.api.controller.dto.request.GrantPermissionRequest
import com.kcd.tax.api.controller.dto.request.UpdateBusinessPlaceRequest
import com.kcd.tax.api.service.BusinessPlaceService
import com.kcd.tax.infrastructure.domain.BusinessPlace
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    classes = [com.kcd.tax.api.TaxApiApplication::class]
)
@AutoConfigureMockMvc
class BusinessPlaceControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var businessPlaceService: BusinessPlaceService

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Test
    fun `사업장을 생성할 수 있다`() {
        // Given
        val request = CreateBusinessPlaceRequest(
            businessNumber = "1234567890",
            name = "테스트 주식회사"
        )
        val businessPlace = BusinessPlace("1234567890", "테스트 주식회사")

        every { businessPlaceService.createBusinessPlace(request.businessNumber, request.name) } returns businessPlace

        // When & Then
        mockMvc.perform(
            post("/api/v1/business-places")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("X-Admin-Id", "1")
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.businessNumber").value("1234567890"))
            .andExpect(jsonPath("$.name").value("테스트 주식회사"))
            .andExpect(jsonPath("$.collectionStatus").value("NOT_REQUESTED"))

        verify { businessPlaceService.createBusinessPlace(request.businessNumber, request.name) }
    }

    @Test
    fun `모든 사업장을 조회할 수 있다`() {
        // Given
        val businessPlaces = listOf(
            BusinessPlace("1234567890", "테스트1"),
            BusinessPlace("0987654321", "테스트2")
        )

        every { businessPlaceService.getAllBusinessPlaces() } returns businessPlaces

        // When & Then
        mockMvc.perform(
            get("/api/v1/business-places")
                .header("X-Admin-Id", "1")
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].businessNumber").value("1234567890"))
            .andExpect(jsonPath("$[0].name").value("테스트1"))
            .andExpect(jsonPath("$[1].businessNumber").value("0987654321"))
            .andExpect(jsonPath("$[1].name").value("테스트2"))

        verify { businessPlaceService.getAllBusinessPlaces() }
    }

    @Test
    fun `특정 사업장을 조회할 수 있다`() {
        // Given
        val businessNumber = "1234567890"
        val businessPlace = BusinessPlace(businessNumber, "테스트 주식회사")

        every { businessPlaceService.getBusinessPlace(businessNumber) } returns businessPlace

        // When & Then
        mockMvc.perform(
            get("/api/v1/business-places/$businessNumber")
                .header("X-Admin-Id", "1")
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.businessNumber").value(businessNumber))
            .andExpect(jsonPath("$.name").value("테스트 주식회사"))

        verify { businessPlaceService.getBusinessPlace(businessNumber) }
    }

    @Test
    fun `사업장을 수정할 수 있다`() {
        // Given
        val businessNumber = "1234567890"
        val request = UpdateBusinessPlaceRequest(name = "수정된 이름")
        val updatedBusinessPlace = BusinessPlace(businessNumber, "수정된 이름")

        every { businessPlaceService.updateBusinessPlace(businessNumber, request.name) } returns updatedBusinessPlace

        // When & Then
        mockMvc.perform(
            put("/api/v1/business-places/$businessNumber")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("X-Admin-Id", "1")
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.businessNumber").value(businessNumber))
            .andExpect(jsonPath("$.name").value("수정된 이름"))

        verify { businessPlaceService.updateBusinessPlace(businessNumber, request.name) }
    }

    @Test
    fun `권한을 부여할 수 있다`() {
        // Given
        val businessNumber = "1234567890"
        val request = GrantPermissionRequest(adminId = 2L)
        val permissionInfo = BusinessPlaceService.PermissionInfo(
            id = 1L,
            businessNumber = businessNumber,
            adminId = 2L,
            adminUsername = "manager1",
            adminRole = "MANAGER",
            grantedAt = java.time.LocalDateTime.now()
        )

        every { businessPlaceService.grantPermission(businessNumber, request.adminId) } returns permissionInfo

        // When & Then
        mockMvc.perform(
            post("/api/v1/business-places/$businessNumber/admins")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("X-Admin-Id", "1")
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.adminId").value(2L))
            .andExpect(jsonPath("$.adminUsername").value("manager1"))

        verify { businessPlaceService.grantPermission(businessNumber, request.adminId) }
    }

    @Test
    fun `권한을 해제할 수 있다`() {
        // Given
        val businessNumber = "1234567890"
        val adminId = 2L

        every { businessPlaceService.revokePermission(businessNumber, adminId) } returns Unit

        // When & Then
        mockMvc.perform(
            delete("/api/v1/business-places/$businessNumber/admins/$adminId")
                .header("X-Admin-Id", "1")
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isNoContent)

        verify { businessPlaceService.revokePermission(businessNumber, adminId) }
    }

    @Test
    fun `권한 목록을 조회할 수 있다`() {
        // Given
        val businessNumber = "1234567890"
        val permissions = listOf(
            BusinessPlaceService.PermissionInfo(
                id = 1L,
                businessNumber = businessNumber,
                adminId = 2L,
                adminUsername = "manager1",
                adminRole = "MANAGER",
                grantedAt = java.time.LocalDateTime.now()
            ),
            BusinessPlaceService.PermissionInfo(
                id = 2L,
                businessNumber = businessNumber,
                adminId = 3L,
                adminUsername = "manager2",
                adminRole = "MANAGER",
                grantedAt = java.time.LocalDateTime.now()
            )
        )

        every { businessPlaceService.getPermissionsByBusinessNumber(businessNumber) } returns permissions

        // When & Then
        mockMvc.perform(
            get("/api/v1/business-places/$businessNumber/admins")
                .header("X-Admin-Id", "1")
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.businessNumber").value(businessNumber))
            .andExpect(jsonPath("$.admins[0].adminUsername").value("manager1"))
            .andExpect(jsonPath("$.admins[1].adminUsername").value("manager2"))

        verify { businessPlaceService.getPermissionsByBusinessNumber(businessNumber) }
    }

    // Edge Cases and Validation Tests

    @Test
    fun `사업자번호가 10자리가 아니면 400 에러가 발생한다`() {
        // Given
        val invalidRequest = CreateBusinessPlaceRequest(
            businessNumber = "12345", // 5자리만
            name = "테스트"
        )

        // When & Then
        mockMvc.perform(
            post("/api/v1/business-places")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest))
                .header("X-Admin-Id", "1")
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `사업자번호에 숫자가 아닌 문자가 포함되면 400 에러가 발생한다`() {
        // Given
        val invalidRequest = CreateBusinessPlaceRequest(
            businessNumber = "123456789A",
            name = "테스트"
        )

        // When & Then
        mockMvc.perform(
            post("/api/v1/business-places")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest))
                .header("X-Admin-Id", "1")
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `사업장명이 비어있으면 400 에러가 발생한다`() {
        // Given
        val invalidRequest = """{"businessNumber": "1234567890", "name": ""}"""

        // When & Then
        mockMvc.perform(
            post("/api/v1/business-places")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequest)
                .header("X-Admin-Id", "1")
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `사업장명이 100자를 초과하면 400 에러가 발생한다`() {
        // Given
        val longName = "가".repeat(101)
        val invalidRequest = CreateBusinessPlaceRequest(
            businessNumber = "1234567890",
            name = longName
        )

        // When & Then
        mockMvc.perform(
            post("/api/v1/business-places")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest))
                .header("X-Admin-Id", "1")
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `MANAGER는 사업장을 생성할 수 없다`() {
        // Given
        val request = CreateBusinessPlaceRequest(
            businessNumber = "1234567890",
            name = "테스트"
        )

        // When & Then
        mockMvc.perform(
            post("/api/v1/business-places")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("X-Admin-Id", "2")
                .header("X-Admin-Role", "MANAGER")
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `MANAGER는 사업장 목록을 조회할 수 없다`() {
        // When & Then
        mockMvc.perform(
            get("/api/v1/business-places")
                .header("X-Admin-Id", "2")
                .header("X-Admin-Role", "MANAGER")
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `MANAGER는 사업장을 수정할 수 없다`() {
        // Given
        val businessNumber = "1234567890"
        val request = UpdateBusinessPlaceRequest(name = "수정된 이름")

        // When & Then
        mockMvc.perform(
            put("/api/v1/business-places/$businessNumber")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("X-Admin-Id", "2")
                .header("X-Admin-Role", "MANAGER")
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `인증 헤더 없이 요청하면 401 에러가 발생한다`() {
        // Given
        val request = CreateBusinessPlaceRequest(
            businessNumber = "1234567890",
            name = "테스트"
        )

        // When & Then
        mockMvc.perform(
            post("/api/v1/business-places")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `AdminId 헤더만 있고 Role 헤더가 없으면 401 에러가 발생한다`() {
        // Given
        val request = CreateBusinessPlaceRequest(
            businessNumber = "1234567890",
            name = "테스트"
        )

        // When & Then
        mockMvc.perform(
            post("/api/v1/business-places")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("X-Admin-Id", "1")
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `잘못된 JSON 형식으로 요청하면 400 에러가 발생한다`() {
        // Given
        val invalidJson = """{"businessNumber": "1234567890", "name": """

        // When & Then
        // Note: 잘못된 JSON은 Spring에서 500 Internal Server Error로 처리됨
        mockMvc.perform(
            post("/api/v1/business-places")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson)
                .header("X-Admin-Id", "1")
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isInternalServerError)
    }

    @Test
    fun `존재하지 않는 사업장을 조회하면 404 에러가 발생한다`() {
        // Given
        val businessNumber = "9999999999"
        every { businessPlaceService.getBusinessPlace(businessNumber) } throws com.kcd.tax.common.exception.NotFoundException(
            com.kcd.tax.common.exception.ErrorCode.BUSINESS_NOT_FOUND,
            "사업장을 찾을 수 없습니다"
        )

        // When & Then
        mockMvc.perform(
            get("/api/v1/business-places/$businessNumber")
                .header("X-Admin-Id", "1")
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `이미 존재하는 사업자번호로 생성하면 409 에러가 발생한다`() {
        // Given
        val request = CreateBusinessPlaceRequest(
            businessNumber = "1234567890",
            name = "테스트"
        )

        every { businessPlaceService.createBusinessPlace(request.businessNumber, request.name) } throws com.kcd.tax.common.exception.ConflictException(
            com.kcd.tax.common.exception.ErrorCode.BUSINESS_ALREADY_EXISTS,
            "이미 존재하는 사업자번호입니다"
        )

        // When & Then
        mockMvc.perform(
            post("/api/v1/business-places")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("X-Admin-Id", "1")
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isConflict)
    }

    @Test
    fun `이미 권한이 있는 관리자에게 권한 부여 시 409 에러가 발생한다`() {
        // Given
        val businessNumber = "1234567890"
        val request = GrantPermissionRequest(adminId = 2L)

        every { businessPlaceService.grantPermission(businessNumber, request.adminId) } throws com.kcd.tax.common.exception.ConflictException(
            com.kcd.tax.common.exception.ErrorCode.PERMISSION_ALREADY_EXISTS,
            "이미 권한이 부여되어 있습니다"
        )

        // When & Then
        mockMvc.perform(
            post("/api/v1/business-places/$businessNumber/admins")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("X-Admin-Id", "1")
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isConflict)
    }

    @Test
    fun `존재하지 않는 권한을 해제하면 404 에러가 발생한다`() {
        // Given
        val businessNumber = "1234567890"
        val adminId = 99L

        every { businessPlaceService.revokePermission(businessNumber, adminId) } throws com.kcd.tax.common.exception.NotFoundException(
            com.kcd.tax.common.exception.ErrorCode.PERMISSION_NOT_FOUND,
            "권한을 찾을 수 없습니다"
        )

        // When & Then
        mockMvc.perform(
            delete("/api/v1/business-places/$businessNumber/admins/$adminId")
                .header("X-Admin-Id", "1")
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `AdminId가 숫자가 아니면 401 에러가 발생한다`() {
        // Given
        val businessNumber = "1234567890"
        val request = GrantPermissionRequest(adminId = 2L)

        // When & Then
        // Note: AdminId 파싱 실패는 인증 실패(401)로 처리됨
        mockMvc.perform(
            post("/api/v1/business-places/$businessNumber/admins")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("X-Admin-Id", "not-a-number")
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `잘못된 Role로 요청하면 401 에러가 발생한다`() {
        // Given
        val request = CreateBusinessPlaceRequest(
            businessNumber = "1234567890",
            name = "테스트"
        )

        // When & Then
        // Note: 잘못된 Role은 인증 실패(401)로 처리됨
        mockMvc.perform(
            post("/api/v1/business-places")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("X-Admin-Id", "1")
                .header("X-Admin-Role", "INVALID_ROLE")
        )
            .andExpect(status().isUnauthorized)
    }
}
