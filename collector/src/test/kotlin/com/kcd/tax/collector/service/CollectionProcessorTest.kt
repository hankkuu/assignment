package com.kcd.tax.collector.service

import com.kcd.tax.common.enums.CollectionStatus
import com.kcd.tax.infrastructure.domain.BusinessPlace
import com.kcd.tax.infrastructure.domain.Transaction
import com.kcd.tax.infrastructure.helper.BusinessPlaceRepositoryHelper
import com.kcd.tax.infrastructure.repository.BusinessPlaceRepository
import com.kcd.tax.infrastructure.repository.TransactionRepository
import com.kcd.tax.collector.util.ExcelParser
import io.mockk.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

/**
 * CollectionProcessor 단위 테스트
 *
 * 테스트 범위:
 * - start(): 수집 시작 (상태 변경, 비관적 락)
 * - complete(): 수집 완료 (거래 데이터 교체, 상태 변경)
 * - fail(): 수집 실패 (상태 복원)
 * - parseTransactions(): Excel 파싱
 */
class CollectionProcessorTest {

    private val businessPlaceHelper: BusinessPlaceRepositoryHelper = mockk()
    private val transactionRepository: TransactionRepository = mockk()
    private val businessPlaceRepository: BusinessPlaceRepository = mockk()
    private val excelParser: ExcelParser = mockk()

    private val processor = CollectionProcessor(
        businessPlaceHelper,
        transactionRepository,
        businessPlaceRepository,
        excelParser
    )

    // ========================================
    // start() 테스트
    // ========================================

    @Test
    fun `start - 수집 상태를 COLLECTING으로 변경한다`() {
        // Given
        val businessNumber = "1234567890"
        val businessPlace = BusinessPlace(
            businessNumber = businessNumber,
            name = "테스트 회사"
        )

        every { businessPlaceRepository.findById(businessNumber) } returns Optional.of(businessPlace)
        every { businessPlaceRepository.save(any()) } returns businessPlace

        // When
        processor.start(businessNumber)

        // Then
        assert(businessPlace.collectionStatus == CollectionStatus.COLLECTING)
        verify(exactly = 1) { businessPlaceRepository.save(businessPlace) }
    }

    @Test
    fun `start - 사업장이 없으면 예외를 발생시킨다`() {
        // Given
        val businessNumber = "1234567890"
        every { businessPlaceRepository.findById(businessNumber) } returns Optional.empty()

        // When & Then
        assertThrows<IllegalStateException> {
            processor.start(businessNumber)
        }

        verify(exactly = 0) { businessPlaceRepository.save(any()) }
    }

    // ========================================
    // complete() 테스트
    // ========================================

    @Test
    fun `complete - 기존 데이터를 삭제하고 새 데이터를 저장한다`() {
        // Given
        val businessNumber = "1234567890"
        val businessPlace = BusinessPlace(
            businessNumber = businessNumber,
            name = "테스트 회사"
        ).apply {
            startCollection()  // 상태를 COLLECTING으로 변경
        }

        val newTransactions = listOf(
            Transaction(
                businessNumber = businessNumber,
                type = com.kcd.tax.common.enums.TransactionType.SALES,
                amount = BigDecimal("1000000"),
                counterpartyName = "고객1",
                transactionDate = LocalDate.now()
            ),
            Transaction(
                businessNumber = businessNumber,
                type = com.kcd.tax.common.enums.TransactionType.PURCHASE,
                amount = BigDecimal("500000"),
                counterpartyName = "공급사1",
                transactionDate = LocalDate.now()
            )
        )

        every { businessPlaceRepository.findById(businessNumber) } returns Optional.of(businessPlace)
        every { transactionRepository.deleteByBusinessNumber(businessNumber) } returns 5
        every { transactionRepository.saveAll<Transaction>(newTransactions) } returns newTransactions
        every { businessPlaceRepository.save(any()) } returns businessPlace

        // When
        processor.complete(businessNumber, newTransactions)

        // Then
        assert(businessPlace.collectionStatus == CollectionStatus.COLLECTED)
        verifyOrder {
            transactionRepository.deleteByBusinessNumber(businessNumber)
            transactionRepository.saveAll(newTransactions)
            businessPlaceRepository.save(businessPlace)
        }
    }

    @Test
    fun `complete - 기존 데이터가 없어도 새 데이터를 저장한다`() {
        // Given
        val businessNumber = "1234567890"
        val businessPlace = BusinessPlace(
            businessNumber = businessNumber,
            name = "테스트 회사"
        ).apply {
            startCollection()
        }

        val newTransactions = listOf(
            Transaction(
                businessNumber = businessNumber,
                type = com.kcd.tax.common.enums.TransactionType.SALES,
                amount = BigDecimal("1000000"),
                counterpartyName = "고객1",
                transactionDate = LocalDate.now()
            )
        )

        every { businessPlaceRepository.findById(businessNumber) } returns Optional.of(businessPlace)
        every { transactionRepository.deleteByBusinessNumber(businessNumber) } returns 0  // 기존 데이터 없음
        every { transactionRepository.saveAll<Transaction>(newTransactions) } returns newTransactions
        every { businessPlaceRepository.save(any()) } returns businessPlace

        // When
        processor.complete(businessNumber, newTransactions)

        // Then
        assert(businessPlace.collectionStatus == CollectionStatus.COLLECTED)
        verify(exactly = 1) { transactionRepository.deleteByBusinessNumber(businessNumber) }
        verify(exactly = 1) { transactionRepository.saveAll(newTransactions) }
    }

    @Test
    fun `complete - 사업장이 없으면 예외를 발생시킨다`() {
        // Given
        val businessNumber = "1234567890"
        val transactions = emptyList<Transaction>()

        every { businessPlaceRepository.findById(businessNumber) } returns Optional.empty()

        // When & Then
        assertThrows<IllegalStateException> {
            processor.complete(businessNumber, transactions)
        }

        verify(exactly = 0) { transactionRepository.deleteByBusinessNumber(any()) }
        verify(exactly = 0) { transactionRepository.saveAll<Transaction>(any()) }
    }

    // ========================================
    // fail() 테스트
    // ========================================

    @Test
    fun `fail - COLLECTING 상태를 NOT_REQUESTED로 복원한다`() {
        // Given
        val businessNumber = "1234567890"
        val businessPlace = BusinessPlace(
            businessNumber = businessNumber,
            name = "테스트 회사"
        ).apply {
            startCollection()  // COLLECTING 상태로 변경
        }

        every { businessPlaceHelper.findByIdOrThrow(businessNumber) } returns businessPlace
        every { businessPlaceHelper.save(any()) } returns businessPlace

        // When
        processor.fail(businessNumber)

        // Then
        assert(businessPlace.collectionStatus == CollectionStatus.NOT_REQUESTED)
        verify(exactly = 1) { businessPlaceHelper.save(businessPlace) }
    }

    @Test
    fun `fail - NOT_REQUESTED 상태는 변경하지 않는다`() {
        // Given
        val businessNumber = "1234567890"
        val businessPlace = BusinessPlace(
            businessNumber = businessNumber,
            name = "테스트 회사"
        )
        // 이미 NOT_REQUESTED 상태

        every { businessPlaceHelper.findByIdOrThrow(businessNumber) } returns businessPlace

        // When
        processor.fail(businessNumber)

        // Then
        assert(businessPlace.collectionStatus == CollectionStatus.NOT_REQUESTED)
        verify(exactly = 0) { businessPlaceHelper.save(any()) }  // 상태가 이미 NOT_REQUESTED이므로 저장 안 함
    }

    @Test
    fun `fail - COLLECTED 상태는 변경하지 않는다`() {
        // Given
        val businessNumber = "1234567890"
        val businessPlace = BusinessPlace(
            businessNumber = businessNumber,
            name = "테스트 회사"
        ).apply {
            startCollection()
            completeCollection()  // COLLECTED 상태로 변경
        }

        every { businessPlaceHelper.findByIdOrThrow(businessNumber) } returns businessPlace

        // When
        processor.fail(businessNumber)

        // Then
        assert(businessPlace.collectionStatus == CollectionStatus.COLLECTED)  // 변경되지 않음
        verify(exactly = 0) { businessPlaceHelper.save(any()) }
    }

    @Test
    fun `fail - 예외가 발생해도 에러를 던지지 않는다`() {
        // Given
        val businessNumber = "1234567890"
        every { businessPlaceHelper.findByIdOrThrow(businessNumber) } throws RuntimeException("DB 오류")

        // When & Then (예외가 발생하지 않아야 함)
        processor.fail(businessNumber)

        verify(exactly = 1) { businessPlaceHelper.findByIdOrThrow(businessNumber) }
        verify(exactly = 0) { businessPlaceHelper.save(any()) }
    }

    // ========================================
    // parseTransactions() 테스트
    // ========================================

    @Test
    fun `parseTransactions - Excel 파일을 파싱하여 거래 내역을 반환한다`() {
        // Given
        val dataFilePath = "test.xlsx"
        val businessNumber = "1234567890"
        val expectedTransactions = listOf(
            Transaction(
                businessNumber = businessNumber,
                type = com.kcd.tax.common.enums.TransactionType.SALES,
                amount = BigDecimal("1000000"),
                counterpartyName = "고객1",
                transactionDate = LocalDate.now()
            )
        )

        every { excelParser.parseExcelFile(dataFilePath, businessNumber) } returns expectedTransactions

        // When
        val result = processor.parseTransactions(dataFilePath, businessNumber)

        // Then
        assert(result == expectedTransactions)
        verify(exactly = 1) { excelParser.parseExcelFile(dataFilePath, businessNumber) }
    }

    @Test
    fun `parseTransactions - 빈 파일은 빈 리스트를 반환한다`() {
        // Given
        val dataFilePath = "empty.xlsx"
        val businessNumber = "1234567890"

        every { excelParser.parseExcelFile(dataFilePath, businessNumber) } returns emptyList()

        // When
        val result = processor.parseTransactions(dataFilePath, businessNumber)

        // Then
        assert(result.isEmpty())
        verify(exactly = 1) { excelParser.parseExcelFile(dataFilePath, businessNumber) }
    }
}
