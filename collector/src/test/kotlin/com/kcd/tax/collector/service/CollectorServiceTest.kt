package com.kcd.tax.collector.service

import com.kcd.tax.infrastructure.domain.Transaction
import io.mockk.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CollectorServiceTest {

    private val collectionProcessor: CollectionProcessor = mockk()
    private val dataFilePath = "sample.xlsx"

    private val service = CollectorService(
        collectionProcessor,
        dataFilePath
    )

    @Test
    fun `데이터 수집이 정상적으로 완료된다`() {
        // Given
        val businessNumber = "1234567890"
        val transactions = emptyList<Transaction>()

        every { collectionProcessor.start(businessNumber) } just Runs
        every { collectionProcessor.parseTransactions(dataFilePath, businessNumber) } returns transactions
        every { collectionProcessor.complete(businessNumber, transactions) } just Runs

        // 5분 대기를 스킵하기 위해 모킹
        mockkStatic(Thread::class)
        every { Thread.sleep(any<Long>()) } just Runs

        // When
        service.collectData(businessNumber)

        // Then
        verifyOrder {
            collectionProcessor.start(businessNumber)
            collectionProcessor.parseTransactions(dataFilePath, businessNumber)
            collectionProcessor.complete(businessNumber, transactions)
        }
        verify(exactly = 0) { collectionProcessor.fail(any()) }

        unmockkStatic(Thread::class)
    }

    @Test
    fun `수집 실패 시 fail이 호출된다`() {
        // Given
        val businessNumber = "1234567890"
        val exception = RuntimeException("Parsing failed")

        every { collectionProcessor.start(businessNumber) } just Runs
        every { collectionProcessor.parseTransactions(dataFilePath, businessNumber) } throws exception
        every { collectionProcessor.fail(businessNumber) } just Runs

        mockkStatic(Thread::class)
        every { Thread.sleep(any<Long>()) } just Runs

        // When & Then
        assertThrows<RuntimeException> {
            service.collectData(businessNumber)
        }

        // Then
        verifyOrder {
            collectionProcessor.start(businessNumber)
            collectionProcessor.parseTransactions(dataFilePath, businessNumber)
            collectionProcessor.fail(businessNumber)
        }
        verify(exactly = 0) { collectionProcessor.complete(any(), any()) }

        unmockkStatic(Thread::class)
    }
}

