package com.kcd.tax.api.service

import com.kcd.tax.common.enums.AdminRole
import com.kcd.tax.common.enums.TransactionType
import com.kcd.tax.common.exception.ForbiddenException
import com.kcd.tax.common.exception.NotFoundException
import com.kcd.tax.infrastructure.domain.BusinessPlace
import com.kcd.tax.infrastructure.helper.BusinessPlaceRepositoryHelper
import com.kcd.tax.infrastructure.repository.BusinessPlaceAdminRepository
import com.kcd.tax.infrastructure.repository.TransactionRepository
import com.kcd.tax.infrastructure.util.VatCalculator
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal

class VatCalculationServiceEdgeCaseTest {

    private val transactionRepository: TransactionRepository = mockk()
    private val businessPlaceHelper: BusinessPlaceRepositoryHelper = mockk()
    private val businessPlaceAdminRepository: BusinessPlaceAdminRepository = mockk()
    private val vatCalculator: VatCalculator = mockk()

    private val service = VatCalculationService(
        transactionRepository,
        businessPlaceHelper,
        businessPlaceAdminRepository,
        vatCalculator
    )

    @Test
    fun `빈 목록으로 부가세 계산 시 빈 결과가 반환된다`() {
        // Given
        val businessNumbers = emptyList<String>()

        // When
        val results = service.calculateVat(businessNumbers)

        // Then
        assertTrue(results.isEmpty())
    }

    @Test
    fun `MANAGER가 권한이 없는 빈 목록을 조회한다`() {
        // Given
        val adminId = 999L
        val role = AdminRole.MANAGER

        every { businessPlaceAdminRepository.findBusinessNumbersByAdminId(adminId) } returns emptyList()

        // When
        val result = service.getAuthorizedBusinessNumbers(adminId, role)

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `사업장이 하나도 없을 때 ADMIN 조회 시 빈 목록이 반환된다`() {
        // Given
        val adminId = 1L
        val role = AdminRole.ADMIN

        every { businessPlaceHelper.findAll() } returns emptyList()

        // When
        val result = service.getAuthorizedBusinessNumbers(adminId, role)

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `여러 사업장 중 하나가 없는 경우 예외가 발생한다`() {
        // Given
        val businessNumbers = listOf("1234567890", "9999999999", "0987654321")
        val businessPlace1 = BusinessPlace("1234567890", "테스트1")

        every { businessPlaceHelper.findByIdOrThrow("1234567890") } returns businessPlace1
        every { businessPlaceHelper.findByIdOrThrow("9999999999") } throws NotFoundException::class.java.getDeclaredConstructor().newInstance()

        // When & Then
        assertThrows<NotFoundException> {
            service.calculateVat(businessNumbers)
        }

        // 첫 번째는 조회되었지만 두 번째에서 실패
        verify { businessPlaceHelper.findByIdOrThrow("1234567890") }
        verify { businessPlaceHelper.findByIdOrThrow("9999999999") }
    }

    @Test
    fun `매출과 매입이 모두 0인 사업장의 부가세는 0이다`() {
        // Given
        val businessNumber = "1234567890"
        val businessPlace = BusinessPlace(businessNumber, "테스트")

        every { businessPlaceHelper.findByIdOrThrow(businessNumber) } returns businessPlace
        every { transactionRepository.sumAmountByBusinessNumberAndType(businessNumber, TransactionType.SALES) } returns BigDecimal.ZERO
        every { transactionRepository.sumAmountByBusinessNumberAndType(businessNumber, TransactionType.PURCHASE) } returns BigDecimal.ZERO
        every { vatCalculator.calculate(BigDecimal.ZERO, BigDecimal.ZERO) } returns 0L

        // When
        val result = service.calculateVatForBusinessPlace(businessNumber)

        // Then
        assertEquals(0L, result.vatAmount)
        assertEquals(BigDecimal.ZERO, result.totalSales)
        assertEquals(BigDecimal.ZERO, result.totalPurchases)
    }

    @Test
    fun `매우 큰 adminId로 MANAGER 권한 조회`() {
        // Given
        val adminId = Long.MAX_VALUE
        val role = AdminRole.MANAGER

        every { businessPlaceAdminRepository.findBusinessNumbersByAdminId(adminId) } returns emptyList()

        // When
        val result = service.getAuthorizedBusinessNumbers(adminId, role)

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `음수 adminId로 MANAGER 권한 조회`() {
        // Given
        val adminId = -1L
        val role = AdminRole.MANAGER

        every { businessPlaceAdminRepository.findBusinessNumbersByAdminId(adminId) } returns emptyList()

        // When
        val result = service.getAuthorizedBusinessNumbers(adminId, role)

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `0번 adminId로 권한 확인`() {
        // Given
        val businessNumber = "1234567890"
        val adminId = 0L
        val role = AdminRole.MANAGER

        every { businessPlaceAdminRepository.existsByBusinessNumberAndAdminId(businessNumber, adminId) } returns false

        // When & Then
        assertThrows<ForbiddenException> {
            service.checkPermission(businessNumber, adminId, role)
        }
    }

    @Test
    fun `같은 사업장에 대해 여러 번 권한 확인`() {
        // Given
        val businessNumber = "1234567890"
        val adminId = 2L
        val role = AdminRole.MANAGER

        every { businessPlaceAdminRepository.existsByBusinessNumberAndAdminId(businessNumber, adminId) } returns true

        // When & Then - 여러 번 호출해도 예외 없음
        repeat(10) {
            assertDoesNotThrow {
                service.checkPermission(businessNumber, adminId, role)
            }
        }

        verify(exactly = 10) { businessPlaceAdminRepository.existsByBusinessNumberAndAdminId(businessNumber, adminId) }
    }

    @Test
    fun `매입이 매출보다 훨씬 큰 경우 큰 음수 부가세`() {
        // Given
        val businessNumber = "1234567890"
        val businessPlace = BusinessPlace(businessNumber, "테스트")
        val totalSales = BigDecimal("1000000")
        val totalPurchases = BigDecimal("100000000")
        val expectedVat = -9000000L

        every { businessPlaceHelper.findByIdOrThrow(businessNumber) } returns businessPlace
        every { transactionRepository.sumAmountByBusinessNumberAndType(businessNumber, TransactionType.SALES) } returns totalSales
        every { transactionRepository.sumAmountByBusinessNumberAndType(businessNumber, TransactionType.PURCHASE) } returns totalPurchases
        every { vatCalculator.calculate(totalSales, totalPurchases) } returns expectedVat

        // When
        val result = service.calculateVatForBusinessPlace(businessNumber)

        // Then
        assertEquals(expectedVat, result.vatAmount)
        assertTrue(result.vatAmount < 0)
    }

    @Test
    fun `특수문자가 포함된 사업자번호로 권한 확인`() {
        // Given
        val businessNumber = "123-45-67890" // 실제로는 DB에서 걸러지지만 서비스 레벨 테스트
        val adminId = 1L
        val role = AdminRole.ADMIN

        // When & Then - ADMIN은 항상 통과
        assertDoesNotThrow {
            service.checkPermission(businessNumber, adminId, role)
        }
    }

    @Test
    fun `매우 많은 사업장 목록으로 부가세 계산`() {
        // Given
        val businessNumbers = (1..100).map { "123456789$it" }.take(10) // 10개만 테스트

        businessNumbers.forEach { businessNumber ->
            val businessPlace = BusinessPlace(businessNumber, "테스트$businessNumber")
            every { businessPlaceHelper.findByIdOrThrow(businessNumber) } returns businessPlace
            every { transactionRepository.sumAmountByBusinessNumberAndType(businessNumber, any()) } returns BigDecimal.ZERO
            every { vatCalculator.calculate(any(), any()) } returns 0L
        }

        // When
        val results = service.calculateVat(businessNumbers)

        // Then
        assertEquals(10, results.size)
    }

    @Test
    fun `null 값을 반환하지 않는 Repository의 경우 정상 처리`() {
        // Given
        val businessNumber = "1234567890"
        val businessPlace = BusinessPlace(businessNumber, "테스트")

        every { businessPlaceHelper.findByIdOrThrow(businessNumber) } returns businessPlace
        every { transactionRepository.sumAmountByBusinessNumberAndType(businessNumber, TransactionType.SALES) } returns BigDecimal.ZERO
        every { transactionRepository.sumAmountByBusinessNumberAndType(businessNumber, TransactionType.PURCHASE) } returns BigDecimal.ZERO
        every { vatCalculator.calculate(any(), any()) } returns 0L

        // When
        val result = service.calculateVatForBusinessPlace(businessNumber)

        // Then
        assertNotNull(result)
        assertNotNull(result.businessNumber)
        assertNotNull(result.businessName)
        assertNotNull(result.totalSales)
        assertNotNull(result.totalPurchases)
    }

    @Test
    fun `사업장명에 특수문자가 포함된 경우도 정상 처리`() {
        // Given
        val businessNumber = "1234567890"
        val businessName = "테스트(주) & <Company> \"명칭\" '특수'"
        val businessPlace = BusinessPlace(businessNumber, businessName)

        every { businessPlaceHelper.findByIdOrThrow(businessNumber) } returns businessPlace
        every { transactionRepository.sumAmountByBusinessNumberAndType(businessNumber, any()) } returns BigDecimal.ZERO
        every { vatCalculator.calculate(any(), any()) } returns 0L

        // When
        val result = service.calculateVatForBusinessPlace(businessNumber)

        // Then
        assertEquals(businessName, result.businessName)
    }
}
