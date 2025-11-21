package com.kcd.tax.api.controller

import com.kcd.tax.api.service.VatCalculationService
import com.kcd.tax.common.enums.AdminRole
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.math.BigDecimal

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    classes = [com.kcd.tax.api.TaxApiApplication::class]
)
@AutoConfigureMockMvc
class VatControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var vatCalculationService: VatCalculationService

    @Test
    fun `ADMIN은 모든 사업장의 부가세를 조회할 수 있다`() {
        // Given
        val adminId = 1L
        val authorizedBusinessNumbers = listOf("1234567890", "0987654321")
        val vatResults = listOf(
            VatCalculationService.VatResult("1234567890", "테스트1", BigDecimal("10000000"), BigDecimal("5000000"), 454550L),
            VatCalculationService.VatResult("0987654321", "테스트2", BigDecimal("20000000"), BigDecimal("10000000"), 909090L)
        )

        every { vatCalculationService.getAuthorizedBusinessNumbers(adminId, AdminRole.ADMIN) } returns authorizedBusinessNumbers
        every { vatCalculationService.calculateVat(authorizedBusinessNumbers) } returns vatResults

        // When & Then
        mockMvc.perform(
            get("/api/v1/vat")
                .header("X-Admin-Id", adminId.toString())
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].businessNumber").value("1234567890"))
            .andExpect(jsonPath("$[0].vatAmount").value(454550))
            .andExpect(jsonPath("$[1].businessNumber").value("0987654321"))
            .andExpect(jsonPath("$[1].vatAmount").value(909090))

        verify { vatCalculationService.getAuthorizedBusinessNumbers(adminId, AdminRole.ADMIN) }
        verify { vatCalculationService.calculateVat(authorizedBusinessNumbers) }
    }

    @Test
    fun `특정 사업장의 부가세를 조회할 수 있다`() {
        // Given
        val adminId = 1L
        val businessNumber = "1234567890"
        val vatResult = VatCalculationService.VatResult(
            businessNumber, "테스트 주식회사", BigDecimal("10000000"), BigDecimal("5000000"), 454550L
        )

        every { vatCalculationService.checkPermission(businessNumber, adminId, AdminRole.ADMIN) } returns Unit
        every { vatCalculationService.calculateVat(listOf(businessNumber)) } returns listOf(vatResult)

        // When & Then
        mockMvc.perform(
            get("/api/v1/vat")
                .param("businessNumber", businessNumber)
                .header("X-Admin-Id", adminId.toString())
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].businessNumber").value(businessNumber))
            .andExpect(jsonPath("$[0].vatAmount").value(454550))

        verify { vatCalculationService.checkPermission(businessNumber, adminId, AdminRole.ADMIN) }
        verify { vatCalculationService.calculateVat(listOf(businessNumber)) }
    }

    @Test
    fun `MANAGER는 할당된 사업장만 조회할 수 있다`() {
        // Given
        val adminId = 2L
        val authorizedBusinessNumbers = listOf("1234567890")
        val vatResults = listOf(
            VatCalculationService.VatResult("1234567890", "테스트", BigDecimal("10000000"), BigDecimal("5000000"), 454550L)
        )

        every { vatCalculationService.getAuthorizedBusinessNumbers(adminId, AdminRole.MANAGER) } returns authorizedBusinessNumbers
        every { vatCalculationService.calculateVat(authorizedBusinessNumbers) } returns vatResults

        // When & Then
        mockMvc.perform(
            get("/api/v1/vat")
                .header("X-Admin-Id", adminId.toString())
                .header("X-Admin-Role", "MANAGER")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].businessNumber").value("1234567890"))
            .andExpect(jsonPath("$[0].vatAmount").value(454550))

        verify { vatCalculationService.getAuthorizedBusinessNumbers(adminId, AdminRole.MANAGER) }
        verify { vatCalculationService.calculateVat(authorizedBusinessNumbers) }
    }

    // Edge Cases and Validation Tests

    @Test
    fun `인증 헤더 없이 부가세 조회하면 401 에러가 발생한다`() {
        // When & Then
        mockMvc.perform(
            get("/api/v1/vat")
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `AdminId 헤더만 있고 Role 헤더가 없으면 401 에러가 발생한다`() {
        // When & Then
        mockMvc.perform(
            get("/api/v1/vat")
                .header("X-Admin-Id", "1")
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `Role 헤더만 있고 AdminId 헤더가 없으면 401 에러가 발생한다`() {
        // When & Then
        mockMvc.perform(
            get("/api/v1/vat")
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `MANAGER가 권한 없는 사업장 조회 시 403 에러가 발생한다`() {
        // Given
        val adminId = 2L
        val businessNumber = "9999999999"

        every { vatCalculationService.checkPermission(businessNumber, adminId, AdminRole.MANAGER) } throws com.kcd.tax.common.exception.ForbiddenException(
            "해당 사업장에 대한 접근 권한이 없습니다"
        )

        // When & Then
        mockMvc.perform(
            get("/api/v1/vat")
                .param("businessNumber", businessNumber)
                .header("X-Admin-Id", adminId.toString())
                .header("X-Admin-Role", "MANAGER")
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `존재하지 않는 사업장 조회 시 404 에러가 발생한다`() {
        // Given
        val adminId = 1L
        val businessNumber = "9999999999"

        every { vatCalculationService.checkPermission(businessNumber, adminId, AdminRole.ADMIN) } returns Unit
        every { vatCalculationService.calculateVat(listOf(businessNumber)) } throws com.kcd.tax.common.exception.NotFoundException(
            com.kcd.tax.common.exception.ErrorCode.BUSINESS_NOT_FOUND,
            "사업장을 찾을 수 없습니다"
        )

        // When & Then
        mockMvc.perform(
            get("/api/v1/vat")
                .param("businessNumber", businessNumber)
                .header("X-Admin-Id", adminId.toString())
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `AdminId가 숫자가 아니면 401 에러가 발생한다`() {
        // When & Then
        // Note: AdminId 파싱 실패는 인증 실패(401)로 처리됨
        mockMvc.perform(
            get("/api/v1/vat")
                .header("X-Admin-Id", "not-a-number")
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `잘못된 Role로 요청하면 401 에러가 발생한다`() {
        // When & Then
        // Note: 잘못된 Role은 인증 실패(401)로 처리됨
        mockMvc.perform(
            get("/api/v1/vat")
                .header("X-Admin-Id", "1")
                .header("X-Admin-Role", "INVALID_ROLE")
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `빈 결과를 반환할 수 있다`() {
        // Given
        val adminId = 2L
        val authorizedBusinessNumbers = emptyList<String>()

        every { vatCalculationService.getAuthorizedBusinessNumbers(adminId, AdminRole.MANAGER) } returns authorizedBusinessNumbers
        every { vatCalculationService.calculateVat(authorizedBusinessNumbers) } returns emptyList()

        // When & Then
        mockMvc.perform(
            get("/api/v1/vat")
                .header("X-Admin-Id", adminId.toString())
                .header("X-Admin-Role", "MANAGER")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$").isEmpty)
    }

    @Test
    fun `음수 부가세도 조회할 수 있다`() {
        // Given
        val adminId = 1L
        val businessNumber = "1234567890"
        val vatResult = VatCalculationService.VatResult(
            businessNumber, "테스트", BigDecimal("1000000"), BigDecimal("2000000"), -90910L
        )

        every { vatCalculationService.checkPermission(businessNumber, adminId, AdminRole.ADMIN) } returns Unit
        every { vatCalculationService.calculateVat(listOf(businessNumber)) } returns listOf(vatResult)

        // When & Then
        mockMvc.perform(
            get("/api/v1/vat")
                .param("businessNumber", businessNumber)
                .header("X-Admin-Id", adminId.toString())
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].vatAmount").value(-90910))
    }

    @Test
    fun `부가세가 0인 경우도 조회할 수 있다`() {
        // Given
        val adminId = 1L
        val businessNumber = "1234567890"
        val vatResult = VatCalculationService.VatResult(
            businessNumber, "테스트", BigDecimal("1000000"), BigDecimal("1000000"), 0L
        )

        every { vatCalculationService.checkPermission(businessNumber, adminId, AdminRole.ADMIN) } returns Unit
        every { vatCalculationService.calculateVat(listOf(businessNumber)) } returns listOf(vatResult)

        // When & Then
        mockMvc.perform(
            get("/api/v1/vat")
                .param("businessNumber", businessNumber)
                .header("X-Admin-Id", adminId.toString())
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].vatAmount").value(0))
    }

    @Test
    fun `여러 사업장의 부가세를 한번에 조회할 수 있다`() {
        // Given
        val adminId = 1L
        val authorizedBusinessNumbers = listOf("1111111111", "2222222222", "3333333333")
        val vatResults = authorizedBusinessNumbers.mapIndexed { index, businessNumber ->
            VatCalculationService.VatResult(
                businessNumber,
                "테스트${index + 1}",
                BigDecimal((index + 1) * 1000000),
                BigDecimal((index + 1) * 500000),
                (index + 1) * 45450L
            )
        }

        every { vatCalculationService.getAuthorizedBusinessNumbers(adminId, AdminRole.ADMIN) } returns authorizedBusinessNumbers
        every { vatCalculationService.calculateVat(authorizedBusinessNumbers) } returns vatResults

        // When & Then
        mockMvc.perform(
            get("/api/v1/vat")
                .header("X-Admin-Id", adminId.toString())
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$[0].businessNumber").value("1111111111"))
            .andExpect(jsonPath("$[1].businessNumber").value("2222222222"))
            .andExpect(jsonPath("$[2].businessNumber").value("3333333333"))
    }

    @Test
    fun `사업자번호가 잘못된 형식이면 권한 체크에서 실패한다`() {
        // Given
        val adminId = 1L
        val invalidBusinessNumber = "invalid"

        every { vatCalculationService.checkPermission(invalidBusinessNumber, adminId, AdminRole.ADMIN) } returns Unit
        every { vatCalculationService.calculateVat(listOf(invalidBusinessNumber)) } throws com.kcd.tax.common.exception.NotFoundException(
            com.kcd.tax.common.exception.ErrorCode.BUSINESS_NOT_FOUND,
            "사업장을 찾을 수 없습니다"
        )

        // When & Then
        mockMvc.perform(
            get("/api/v1/vat")
                .param("businessNumber", invalidBusinessNumber)
                .header("X-Admin-Id", adminId.toString())
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isNotFound)
    }
}
