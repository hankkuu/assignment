package com.kcd.tax.collector.service

import com.kcd.tax.common.enums.CollectionStatus
import com.kcd.tax.common.exception.NotFoundException
import com.kcd.tax.infrastructure.domain.BusinessPlace
import com.kcd.tax.infrastructure.domain.Transaction
import com.kcd.tax.infrastructure.helper.BusinessPlaceRepositoryHelper
import com.kcd.tax.infrastructure.repository.TransactionRepository
import com.kcd.tax.infrastructure.util.ExcelParser
import io.mockk.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows

class CollectorServiceTest {

    private val businessPlaceHelper: BusinessPlaceRepositoryHelper = mockk()
    private val transactionRepository: TransactionRepository = mockk()
    private val excelParser: ExcelParser = mockk()
    private val dataFilePath = "sample.xlsx"

    private val service = CollectorService(
        businessPlaceHelper,
        transactionRepository,
        excelParser,
        dataFilePath
    )

    @Test
    fun `데이터 수집이 정상적으로 완료된다`() {
        // Given
        val businessNumber = "1234567890"
        val businessPlace = BusinessPlace(businessNumber, "테스트 주식회사")
        val transactions = emptyList<Transaction>()

        every { businessPlaceHelper.findByIdOrThrow(businessNumber) } returns businessPlace
        every { businessPlaceHelper.save(any()) } returns businessPlace
        every { excelParser.parseExcelFile(dataFilePath, businessNumber) } returns transactions
        every { transactionRepository.deleteByBusinessNumber(businessNumber) } returns 0
        every { transactionRepository.saveAll(transactions) } returns transactions

        // 5분 대기를 스킵하기 위해 모킹 (실제 테스트에서는 시간이 오래 걸림)
        mockkStatic(Thread::class)
        every { Thread.sleep(any<Long>()) } just Runs

        // When
        service.collectData(businessNumber)

        // Then
        verify { businessPlaceHelper.findByIdOrThrow(businessNumber) }
        verify(exactly = 2) { businessPlaceHelper.save(businessPlace) } // startCollection, completeCollection
        verify { excelParser.parseExcelFile(dataFilePath, businessNumber) }
        verify { transactionRepository.deleteByBusinessNumber(businessNumber) }
        verify { transactionRepository.saveAll(transactions) }

        assertEquals(CollectionStatus.COLLECTED, businessPlace.collectionStatus)

        unmockkStatic(Thread::class)
    }

    @Test
    fun `존재하지 않는 사업장 수집 시 예외가 발생한다`() {
        // Given
        val businessNumber = "9999999999"
        every { businessPlaceHelper.findByIdOrThrow(businessNumber) } throws NotFoundException::class.java.getDeclaredConstructor().newInstance()

        // When & Then
        assertThrows<NotFoundException> {
            service.collectData(businessNumber)
        }

        verify { businessPlaceHelper.findByIdOrThrow(businessNumber) }
        verify(exactly = 0) { businessPlaceHelper.save(any()) }
    }

    @Test
    fun `수집 실패 시 상태가 복원된다`() {
        // Given
        val businessNumber = "1234567890"
        val businessPlace = BusinessPlace(businessNumber, "테스트 주식회사")

        every { businessPlaceHelper.findByIdOrThrow(businessNumber) } returns businessPlace
        every { businessPlaceHelper.save(any()) } returns businessPlace
        every { excelParser.parseExcelFile(any(), any()) } throws RuntimeException("파싱 실패")

        mockkStatic(Thread::class)
        every { Thread.sleep(any<Long>()) } just Runs

        // When & Then
        assertThrows<RuntimeException> {
            service.collectData(businessNumber)
        }

        // 상태가 NOT_REQUESTED로 복원되었는지 확인
        assertEquals(CollectionStatus.NOT_REQUESTED, businessPlace.collectionStatus)

        verify { businessPlaceHelper.findByIdOrThrow(businessNumber) }
        verify(atLeast = 2) { businessPlaceHelper.save(businessPlace) } // startCollection, resetCollection

        unmockkStatic(Thread::class)
    }

    @Test
    fun `기존 데이터가 있을 경우 삭제 후 새 데이터를 저장한다`() {
        // Given
        val businessNumber = "1234567890"
        val businessPlace = BusinessPlace(businessNumber, "테스트 주식회사")
        val transactions = emptyList<Transaction>()
        val deletedCount = 100

        every { businessPlaceHelper.findByIdOrThrow(businessNumber) } returns businessPlace
        every { businessPlaceHelper.save(any()) } returns businessPlace
        every { excelParser.parseExcelFile(dataFilePath, businessNumber) } returns transactions
        every { transactionRepository.deleteByBusinessNumber(businessNumber) } returns deletedCount
        every { transactionRepository.saveAll(transactions) } returns transactions

        mockkStatic(Thread::class)
        every { Thread.sleep(any<Long>()) } just Runs

        // When
        service.collectData(businessNumber)

        // Then
        verify { transactionRepository.deleteByBusinessNumber(businessNumber) }
        verify { transactionRepository.saveAll(transactions) }

        unmockkStatic(Thread::class)
    }

    @Test
    fun `수집 중 상태가 COLLECTING으로 변경된다`() {
        // Given
        val businessNumber = "1234567890"
        val businessPlace = BusinessPlace(businessNumber, "테스트 주식회사")
        val transactions = emptyList<Transaction>()

        var statusAfterStart: CollectionStatus? = null

        every { businessPlaceHelper.findByIdOrThrow(businessNumber) } returns businessPlace
        every { businessPlaceHelper.save(any()) } answers {
            if (statusAfterStart == null) {
                statusAfterStart = businessPlace.collectionStatus
            }
            firstArg()
        }
        every { excelParser.parseExcelFile(dataFilePath, businessNumber) } returns transactions
        every { transactionRepository.deleteByBusinessNumber(businessNumber) } returns 0
        every { transactionRepository.saveAll(transactions) } returns transactions

        mockkStatic(Thread::class)
        every { Thread.sleep(any<Long>()) } just Runs

        // When
        service.collectData(businessNumber)

        // Then
        assertEquals(CollectionStatus.COLLECTING, statusAfterStart)
        assertEquals(CollectionStatus.COLLECTED, businessPlace.collectionStatus)

        unmockkStatic(Thread::class)
    }
}
