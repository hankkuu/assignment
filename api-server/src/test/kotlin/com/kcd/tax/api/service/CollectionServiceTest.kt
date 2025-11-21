package com.kcd.tax.api.service

import com.kcd.tax.common.enums.CollectionStatus
import com.kcd.tax.common.exception.ConflictException
import com.kcd.tax.common.exception.NotFoundException
import com.kcd.tax.infrastructure.domain.BusinessPlace
import com.kcd.tax.infrastructure.repository.BusinessPlaceRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows
import java.util.*

class CollectionServiceTest {

    private val businessPlaceRepository: BusinessPlaceRepository = mockk()
    private val service = CollectionService(businessPlaceRepository)

    @Test
    fun `NOT_REQUESTED 상태의 사업장에 수집을 요청할 수 있다`() {
        // Given
        val businessNumber = "1234567890"
        val businessPlace = BusinessPlace(businessNumber, "테스트")

        every { businessPlaceRepository.findById(businessNumber) } returns Optional.of(businessPlace)

        // When
        val result = service.requestCollection(businessNumber)

        // Then
        assertEquals(CollectionStatus.NOT_REQUESTED, result)
        assertEquals(CollectionStatus.NOT_REQUESTED, businessPlace.collectionStatus)

        verify { businessPlaceRepository.findById(businessNumber) }
    }

    @Test
    fun `존재하지 않는 사업장에 수집 요청 시 예외가 발생한다`() {
        // Given
        val businessNumber = "9999999999"
        every { businessPlaceRepository.findById(businessNumber) } returns Optional.empty()

        // When & Then
        assertThrows<NotFoundException> {
            service.requestCollection(businessNumber)
        }

        verify { businessPlaceRepository.findById(businessNumber) }
    }

    @Test
    fun `COLLECTING 상태의 사업장에 수집 요청 시 예외가 발생한다`() {
        // Given
        val businessNumber = "1234567890"
        val businessPlace = BusinessPlace(businessNumber, "테스트")
        businessPlace.startCollection() // COLLECTING 상태로 변경

        every { businessPlaceRepository.findById(businessNumber) } returns Optional.of(businessPlace)

        // When & Then
        assertThrows<ConflictException> {
            service.requestCollection(businessNumber)
        }

        verify { businessPlaceRepository.findById(businessNumber) }
    }

    @Test
    fun `COLLECTED 상태의 사업장에는 수집을 요청할 수 없다`() {
        // Given
        val businessNumber = "1234567890"
        val businessPlace = BusinessPlace(businessNumber, "테스트")
        businessPlace.startCollection()
        businessPlace.completeCollection() // COLLECTED 상태로 변경

        every { businessPlaceRepository.findById(businessNumber) } returns Optional.of(businessPlace)

        // When & Then
        assertThrows<ConflictException> {
            service.requestCollection(businessNumber)
        }

        verify { businessPlaceRepository.findById(businessNumber) }
    }

    @Test
    fun `수집 상태를 조회할 수 있다`() {
        // Given
        val businessNumber = "1234567890"
        val businessPlace = BusinessPlace(businessNumber, "테스트")
        businessPlace.startCollection()

        every { businessPlaceRepository.findById(businessNumber) } returns Optional.of(businessPlace)

        // When
        val result = service.getCollectionStatus(businessNumber)

        // Then
        assertEquals(CollectionStatus.COLLECTING, result)

        verify { businessPlaceRepository.findById(businessNumber) }
    }

    @Test
    fun `존재하지 않는 사업장의 상태 조회 시 예외가 발생한다`() {
        // Given
        val businessNumber = "9999999999"
        every { businessPlaceRepository.findById(businessNumber) } returns Optional.empty()

        // When & Then
        assertThrows<NotFoundException> {
            service.getCollectionStatus(businessNumber)
        }

        verify { businessPlaceRepository.findById(businessNumber) }
    }
}
