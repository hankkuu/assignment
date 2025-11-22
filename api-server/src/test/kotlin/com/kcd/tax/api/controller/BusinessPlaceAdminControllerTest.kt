package com.kcd.tax.api.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.kcd.tax.api.controller.dto.request.GrantPermissionRequest
import com.kcd.tax.api.service.BusinessPlaceService
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

/**
 * BusinessPlaceAdminController 테스트
 *
 * 사업장-관리자 권한 관리 기능 테스트
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    classes = [com.kcd.tax.api.TaxApiApplication::class]
)
@AutoConfigureMockMvc
class BusinessPlaceAdminControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var businessPlaceService: BusinessPlaceService

    @Autowired
    private lateinit var objectMapper: ObjectMapper

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
    fun `MANAGER는 권한을 부여할 수 없다`() {
        // Given
        val businessNumber = "1234567890"
        val request = GrantPermissionRequest(adminId = 3L)

        // When & Then
        mockMvc.perform(
            post("/api/v1/business-places/$businessNumber/admins")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("X-Admin-Id", "2")
                .header("X-Admin-Role", "MANAGER")
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `MANAGER는 권한 목록을 조회할 수 없다`() {
        // Given
        val businessNumber = "1234567890"

        // When & Then
        mockMvc.perform(
            get("/api/v1/business-places/$businessNumber/admins")
                .header("X-Admin-Id", "2")
                .header("X-Admin-Role", "MANAGER")
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `MANAGER는 권한을 해제할 수 없다`() {
        // Given
        val businessNumber = "1234567890"
        val adminId = 3L

        // When & Then
        mockMvc.perform(
            delete("/api/v1/business-places/$businessNumber/admins/$adminId")
                .header("X-Admin-Id", "2")
                .header("X-Admin-Role", "MANAGER")
        )
            .andExpect(status().isForbidden)
    }
}
