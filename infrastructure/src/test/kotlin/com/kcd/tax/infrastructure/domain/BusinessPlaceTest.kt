package com.kcd.tax.infrastructure.domain

import com.kcd.tax.common.enums.CollectionStatus
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

class BusinessPlaceTest {

    @Test
    fun `사업장을 생성할 수 있다`() {
        // Given & When
        val businessPlace = BusinessPlace(
            businessNumber = "1234567890",
            name = "테스트 주식회사"
        )

        // Then
        assertEquals("1234567890", businessPlace.businessNumber)
        assertEquals("테스트 주식회사", businessPlace.name)
        assertEquals(CollectionStatus.NOT_REQUESTED, businessPlace.collectionStatus)
    }

    @Test
    fun `NOT_REQUESTED 상태에서 수집을 시작할 수 있다`() {
        // Given
        val businessPlace = BusinessPlace(
            businessNumber = "1234567890",
            name = "테스트 주식회사"
        )

        // When
        businessPlace.collectionRequestedAt = LocalDateTime.now()
        businessPlace.startCollection()

        // Then
        assertEquals(CollectionStatus.COLLECTING, businessPlace.collectionStatus)
    }

    @Test
    fun `COLLECTING 상태에서 수집을 시작하면 예외가 발생한다`() {
        // Given
        val businessPlace = BusinessPlace(
            businessNumber = "1234567890",
            name = "테스트 주식회사"
        )
        businessPlace.collectionRequestedAt = LocalDateTime.now()
        businessPlace.startCollection()

        // When & Then
        val exception = assertThrows<IllegalArgumentException> {
            businessPlace.startCollection()
        }
        assertTrue(exception.message!!.contains("NOT_REQUESTED"))
    }

    @Test
    fun `COLLECTING 상태에서 수집을 완료할 수 있다`() {
        // Given
        val businessPlace = BusinessPlace(
            businessNumber = "1234567890",
            name = "테스트 주식회사"
        )
        businessPlace.collectionRequestedAt = LocalDateTime.now()
        businessPlace.startCollection()

        // When
        businessPlace.completeCollection()

        // Then
        assertEquals(CollectionStatus.COLLECTED, businessPlace.collectionStatus)
    }

    @Test
    fun `NOT_REQUESTED 상태에서 수집을 완료하면 예외가 발생한다`() {
        // Given
        val businessPlace = BusinessPlace(
            businessNumber = "1234567890",
            name = "테스트 주식회사"
        )

        // When & Then
        val exception = assertThrows<IllegalArgumentException> {
            businessPlace.completeCollection()
        }
        assertTrue(exception.message!!.contains("COLLECTING"))
    }

    @Test
    fun `어떤 상태에서든 수집 상태를 리셋할 수 있다`() {
        // Given
        val businessPlace = BusinessPlace(
            businessNumber = "1234567890",
            name = "테스트 주식회사"
        )
        businessPlace.collectionRequestedAt = LocalDateTime.now()
        businessPlace.startCollection()

        // When
        businessPlace.resetCollection()

        // Then
        assertEquals(CollectionStatus.NOT_REQUESTED, businessPlace.collectionStatus)
    }

    @Test
    fun `COLLECTED 상태에서도 리셋할 수 있다`() {
        // Given
        val businessPlace = BusinessPlace(
            businessNumber = "1234567890",
            name = "테스트 주식회사"
        )
        businessPlace.collectionRequestedAt = LocalDateTime.now()
        businessPlace.startCollection()
        businessPlace.completeCollection()

        // When
        businessPlace.resetCollection()

        // Then
        assertEquals(CollectionStatus.NOT_REQUESTED, businessPlace.collectionStatus)
    }

    @Test
    fun `사업장명을 수정할 수 있다`() {
        // Given
        val businessPlace = BusinessPlace(
            businessNumber = "1234567890",
            name = "테스트 주식회사"
        )

        // When
        businessPlace.name = "수정된 주식회사"

        // Then
        assertEquals("수정된 주식회사", businessPlace.name)
    }

    @Test
    fun `같은 사업자번호를 가진 사업장은 동일하다`() {
        // Given
        val businessPlace1 = BusinessPlace(
            businessNumber = "1234567890",
            name = "테스트1"
        )
        val businessPlace2 = BusinessPlace(
            businessNumber = "1234567890",
            name = "테스트2"
        )

        // When & Then
        assertEquals(businessPlace1, businessPlace2)
        assertEquals(businessPlace1.hashCode(), businessPlace2.hashCode())
    }

    @Test
    fun `다른 사업자번호를 가진 사업장은 동일하지 않다`() {
        // Given
        val businessPlace1 = BusinessPlace(
            businessNumber = "1234567890",
            name = "테스트"
        )
        val businessPlace2 = BusinessPlace(
            businessNumber = "0987654321",
            name = "테스트"
        )

        // When & Then
        assertNotEquals(businessPlace1, businessPlace2)
    }

    @Test
    fun `toString은 주요 정보를 포함한다`() {
        // Given
        val businessPlace = BusinessPlace(
            businessNumber = "1234567890",
            name = "테스트 주식회사"
        )

        // When
        val string = businessPlace.toString()

        // Then
        assertTrue(string.contains("1234567890"))
        assertTrue(string.contains("테스트 주식회사"))
        assertTrue(string.contains("NOT_REQUESTED"))
    }

    // Edge Cases

    @Test
    fun `COLLECTED 상태에서 수집을 시작하면 예외가 발생한다`() {
        // Given
        val businessPlace = BusinessPlace(
            businessNumber = "1234567890",
            name = "테스트 주식회사"
        )
        businessPlace.collectionRequestedAt = LocalDateTime.now()
        businessPlace.startCollection()
        businessPlace.completeCollection()

        // When & Then
        val exception = assertThrows<IllegalArgumentException> {
            businessPlace.startCollection()
        }
        assertTrue(exception.message!!.contains("NOT_REQUESTED"))
    }

    @Test
    fun `COLLECTED 상태에서 수집을 완료하면 예외가 발생한다`() {
        // Given
        val businessPlace = BusinessPlace(
            businessNumber = "1234567890",
            name = "테스트 주식회사"
        )
        businessPlace.collectionRequestedAt = LocalDateTime.now()
        businessPlace.startCollection()
        businessPlace.completeCollection()

        // When & Then
        val exception = assertThrows<IllegalArgumentException> {
            businessPlace.completeCollection()
        }
        assertTrue(exception.message!!.contains("COLLECTING"))
    }

    @Test
    fun `빈 문자열 이름으로 사업장을 생성할 수 있다`() {
        // Given & When
        val businessPlace = BusinessPlace(
            businessNumber = "1234567890",
            name = ""
        )

        // Then
        assertEquals("", businessPlace.name)
    }

    @Test
    fun `매우 긴 이름으로 사업장을 생성할 수 있다`() {
        // Given
        val longName = "가".repeat(100)

        // When
        val businessPlace = BusinessPlace(
            businessNumber = "1234567890",
            name = longName
        )

        // Then
        assertEquals(longName, businessPlace.name)
    }

    @Test
    fun `특수문자가 포함된 이름으로 사업장을 생성할 수 있다`() {
        // Given & When
        val businessPlace = BusinessPlace(
            businessNumber = "1234567890",
            name = "테스트(주) & 컴퍼니 #123"
        )

        // Then
        assertEquals("테스트(주) & 컴퍼니 #123", businessPlace.name)
    }

    @Test
    fun `여러 번 리셋할 수 있다`() {
        // Given
        val businessPlace = BusinessPlace(
            businessNumber = "1234567890",
            name = "테스트 주식회사"
        )

        // When
        businessPlace.resetCollection()
        businessPlace.resetCollection()
        businessPlace.resetCollection()

        // Then
        assertEquals(CollectionStatus.NOT_REQUESTED, businessPlace.collectionStatus)
    }

    @Test
    fun `리셋 후 다시 수집을 시작할 수 있다`() {
        // Given
        val businessPlace = BusinessPlace(
            businessNumber = "1234567890",
            name = "테스트 주식회사"
        )
        businessPlace.collectionRequestedAt = LocalDateTime.now()
        businessPlace.startCollection()
        businessPlace.resetCollection()

        // When
        businessPlace.collectionRequestedAt = LocalDateTime.now()
        businessPlace.startCollection()

        // Then
        assertEquals(CollectionStatus.COLLECTING, businessPlace.collectionStatus)
    }

    @Test
    fun `완전한 수집 사이클을 여러 번 반복할 수 있다`() {
        // Given
        val businessPlace = BusinessPlace(
            businessNumber = "1234567890",
            name = "테스트 주식회사"
        )

        // When & Then - 첫 번째 사이클
        businessPlace.collectionRequestedAt = LocalDateTime.now()
        businessPlace.startCollection()
        assertEquals(CollectionStatus.COLLECTING, businessPlace.collectionStatus)
        businessPlace.completeCollection()
        assertEquals(CollectionStatus.COLLECTED, businessPlace.collectionStatus)

        // 두 번째 사이클
        businessPlace.resetCollection()
        assertEquals(CollectionStatus.NOT_REQUESTED, businessPlace.collectionStatus)
        businessPlace.collectionRequestedAt = LocalDateTime.now()
        businessPlace.startCollection()
        assertEquals(CollectionStatus.COLLECTING, businessPlace.collectionStatus)
        businessPlace.completeCollection()
        assertEquals(CollectionStatus.COLLECTED, businessPlace.collectionStatus)
    }

    @Test
    fun `null과 비교 시 동일하지 않다`() {
        // Given
        val businessPlace = BusinessPlace(
            businessNumber = "1234567890",
            name = "테스트"
        )

        // When & Then
        assertNotEquals(businessPlace, null)
    }

    @Test
    fun `다른 타입과 비교 시 동일하지 않다`() {
        // Given
        val businessPlace = BusinessPlace(
            businessNumber = "1234567890",
            name = "테스트"
        )

        // When & Then
        @Suppress("ReplaceCallWithBinaryOperator")
        assertFalse(businessPlace.equals("1234567890"))
    }

    @Test
    fun `자기 자신과 비교 시 동일하다`() {
        // Given
        val businessPlace = BusinessPlace(
            businessNumber = "1234567890",
            name = "테스트"
        )

        // When & Then
        assertEquals(businessPlace, businessPlace)
    }

    @Test
    fun `10자리 사업자번호로 생성할 수 있다`() {
        // Given & When
        val businessPlace = BusinessPlace(
            businessNumber = "1234567890",
            name = "테스트"
        )

        // Then
        assertEquals(10, businessPlace.businessNumber.length)
    }

    @Test
    fun `숫자만 포함된 사업자번호로 생성할 수 있다`() {
        // Given & When
        val businessPlace = BusinessPlace(
            businessNumber = "0000000000",
            name = "테스트"
        )

        // Then
        assertEquals("0000000000", businessPlace.businessNumber)
    }
}
