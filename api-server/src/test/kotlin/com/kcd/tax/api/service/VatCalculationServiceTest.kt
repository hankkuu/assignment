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
import com.kcd.tax.infrastructure.repository.TransactionSumResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal

class VatCalculationServiceTest {

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
    fun `단일 사업장의 부가세를 계산할 수 있다`() {
        // Given
        val businessNumber = "1234567890"
        val businessPlace = BusinessPlace(businessNumber, "테스트 주식회사")
        val totalSales = BigDecimal("10000000")
        val totalPurchases = BigDecimal("5000000")
        val expectedVat = 454550L

        every { businessPlaceHelper.findByIdOrThrow(businessNumber) } returns businessPlace
        every { transactionRepository.sumAmountByBusinessNumberAndType(businessNumber, TransactionType.SALES) } returns totalSales
        every { transactionRepository.sumAmountByBusinessNumberAndType(businessNumber, TransactionType.PURCHASE) } returns totalPurchases
        every { vatCalculator.calculate(totalSales, totalPurchases) } returns expectedVat

        // When
        val result = service.calculateVatForBusinessPlace(businessNumber)

        // Then
        assertEquals(businessNumber, result.businessNumber)
        assertEquals("테스트 주식회사", result.businessName)
        assertEquals(totalSales, result.totalSales)
        assertEquals(totalPurchases, result.totalPurchases)
        assertEquals(expectedVat, result.vatAmount)

        verify { businessPlaceHelper.findByIdOrThrow(businessNumber) }
        verify { transactionRepository.sumAmountByBusinessNumberAndType(businessNumber, TransactionType.SALES) }
        verify { transactionRepository.sumAmountByBusinessNumberAndType(businessNumber, TransactionType.PURCHASE) }
        verify { vatCalculator.calculate(totalSales, totalPurchases) }
    }

    @Test
    fun `존재하지 않는 사업장의 부가세 계산 시 예외가 발생한다`() {
        // Given
        val businessNumber = "9999999999"
        every { businessPlaceHelper.findByIdOrThrow(businessNumber) } throws NotFoundException(
            com.kcd.tax.common.exception.ErrorCode.BUSINESS_NOT_FOUND,
            "사업장을 찾을 수 없습니다"
        )

        // When & Then
        assertThrows<NotFoundException> {
            service.calculateVatForBusinessPlace(businessNumber)
        }
    }

    @Test
    fun `여러 사업장의 부가세를 계산할 수 있다`() {
        // Given
        val businessNumbers = listOf("1234567890", "0987654321")
        val businessPlace1 = BusinessPlace("1234567890", "테스트1")
        val businessPlace2 = BusinessPlace("0987654321", "테스트2")

        // N+1 Query 방지를 위한 bulk 쿼리 mock
        every { businessPlaceHelper.findAllByIds(businessNumbers) } returns listOf(businessPlace1, businessPlace2)
        every { transactionRepository.sumAmountByBusinessNumbersAndType(businessNumbers, TransactionType.SALES) } returns listOf(
            TransactionSumResult("1234567890", BigDecimal.ZERO),
            TransactionSumResult("0987654321", BigDecimal.ZERO)
        )
        every { transactionRepository.sumAmountByBusinessNumbersAndType(businessNumbers, TransactionType.PURCHASE) } returns listOf(
            TransactionSumResult("1234567890", BigDecimal.ZERO),
            TransactionSumResult("0987654321", BigDecimal.ZERO)
        )
        every { vatCalculator.calculate(any(), any()) } returns 0L

        // When
        val results = service.calculateVat(businessNumbers)

        // Then
        assertEquals(2, results.size)
        assertEquals("1234567890", results[0].businessNumber)
        assertEquals("0987654321", results[1].businessNumber)
    }

    @Test
    fun `ADMIN은 모든 사업장을 조회할 수 있다`() {
        // Given
        val adminId = 1L
        val role = AdminRole.ADMIN
        val businessPlaces = listOf(
            BusinessPlace("1234567890", "테스트1"),
            BusinessPlace("0987654321", "테스트2"),
            BusinessPlace("1111111111", "테스트3")
        )

        every { businessPlaceHelper.findAll() } returns businessPlaces

        // When
        val result = service.getAuthorizedBusinessNumbers(adminId, role)

        // Then
        assertEquals(3, result.size)
        assertTrue(result.contains("1234567890"))
        assertTrue(result.contains("0987654321"))
        assertTrue(result.contains("1111111111"))

        verify { businessPlaceHelper.findAll() }
        verify(exactly = 0) { businessPlaceAdminRepository.findBusinessNumbersByAdminId(any()) }
    }

    @Test
    fun `MANAGER는 할당된 사업장만 조회할 수 있다`() {
        // Given
        val adminId = 2L
        val role = AdminRole.MANAGER
        val authorizedBusinessNumbers = listOf("1234567890", "0987654321")

        every { businessPlaceAdminRepository.findBusinessNumbersByAdminId(adminId) } returns authorizedBusinessNumbers

        // When
        val result = service.getAuthorizedBusinessNumbers(adminId, role)

        // Then
        assertEquals(2, result.size)
        assertTrue(result.contains("1234567890"))
        assertTrue(result.contains("0987654321"))

        verify { businessPlaceAdminRepository.findBusinessNumbersByAdminId(adminId) }
        verify(exactly = 0) { businessPlaceHelper.findAll() }
    }

    @Test
    fun `ADMIN은 모든 사업장에 접근 권한이 있다`() {
        // Given
        val businessNumber = "1234567890"
        val adminId = 1L
        val role = AdminRole.ADMIN

        // When & Then - 예외가 발생하지 않아야 함
        assertDoesNotThrow {
            service.checkPermission(businessNumber, adminId, role)
        }

        // businessPlaceAdminRepository는 호출되지 않아야 함
        verify(exactly = 0) { businessPlaceAdminRepository.existsByBusinessNumberAndAdminId(any(), any()) }
    }

    @Test
    fun `MANAGER는 할당된 사업장에만 접근 권한이 있다`() {
        // Given
        val businessNumber = "1234567890"
        val adminId = 2L
        val role = AdminRole.MANAGER

        every { businessPlaceAdminRepository.existsByBusinessNumberAndAdminId(businessNumber, adminId) } returns true

        // When & Then
        assertDoesNotThrow {
            service.checkPermission(businessNumber, adminId, role)
        }

        verify { businessPlaceAdminRepository.existsByBusinessNumberAndAdminId(businessNumber, adminId) }
    }

    @Test
    fun `MANAGER가 할당되지 않은 사업장에 접근하면 예외가 발생한다`() {
        // Given
        val businessNumber = "1234567890"
        val adminId = 2L
        val role = AdminRole.MANAGER

        every { businessPlaceAdminRepository.existsByBusinessNumberAndAdminId(businessNumber, adminId) } returns false

        // When & Then
        assertThrows<ForbiddenException> {
            service.checkPermission(businessNumber, adminId, role)
        }

        verify { businessPlaceAdminRepository.existsByBusinessNumberAndAdminId(businessNumber, adminId) }
    }

    @Test
    fun `매출이 0인 경우에도 부가세를 계산할 수 있다`() {
        // Given
        val businessNumber = "1234567890"
        val businessPlace = BusinessPlace(businessNumber, "테스트")
        val totalSales = BigDecimal.ZERO
        val totalPurchases = BigDecimal("1000000")
        val expectedVat = -90910L

        every { businessPlaceHelper.findByIdOrThrow(businessNumber) } returns businessPlace
        every { transactionRepository.sumAmountByBusinessNumberAndType(businessNumber, TransactionType.SALES) } returns totalSales
        every { transactionRepository.sumAmountByBusinessNumberAndType(businessNumber, TransactionType.PURCHASE) } returns totalPurchases
        every { vatCalculator.calculate(totalSales, totalPurchases) } returns expectedVat

        // When
        val result = service.calculateVatForBusinessPlace(businessNumber)

        // Then
        assertEquals(expectedVat, result.vatAmount)
    }
}
