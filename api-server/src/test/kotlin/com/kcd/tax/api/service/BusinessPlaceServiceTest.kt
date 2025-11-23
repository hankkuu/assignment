package com.kcd.tax.api.service

import com.kcd.tax.common.enums.AdminRole
import com.kcd.tax.common.exception.ConflictException
import com.kcd.tax.common.exception.NotFoundException
import com.kcd.tax.infrastructure.domain.Admin
import com.kcd.tax.infrastructure.domain.BusinessPlace
import com.kcd.tax.infrastructure.domain.BusinessPlaceAdmin
import com.kcd.tax.infrastructure.helper.BusinessPlaceRepositoryHelper
import com.kcd.tax.infrastructure.repository.AdminRepository
import com.kcd.tax.infrastructure.repository.BusinessPlaceAdminRepository
import com.kcd.tax.infrastructure.repository.BusinessPlaceAdminDetail
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import java.util.*

class BusinessPlaceServiceTest {

    private val businessPlaceHelper: BusinessPlaceRepositoryHelper = mockk()
    private val adminRepository: AdminRepository = mockk()
    private val businessPlaceAdminRepository: BusinessPlaceAdminRepository = mockk()

    private val service = BusinessPlaceService(
        businessPlaceHelper,
        adminRepository,
        businessPlaceAdminRepository
    )

    @Test
    fun `사업장을 생성할 수 있다`() {
        // Given
        val businessNumber = "1234567890"
        val name = "테스트 주식회사"
        val slot = slot<BusinessPlace>()

        every { businessPlaceHelper.throwIfExists(businessNumber) } returns Unit
        every { businessPlaceHelper.save(capture(slot)) } answers { slot.captured }

        // When
        val result = service.createBusinessPlace(businessNumber, name)

        // Then
        assertEquals(businessNumber, result.businessNumber)
        assertEquals(name, result.name)

        verify { businessPlaceHelper.throwIfExists(businessNumber) }
        verify { businessPlaceHelper.save(any()) }
    }

    @Test
    fun `이미 존재하는 사업자번호로 생성 시 예외가 발생한다`() {
        // Given
        val businessNumber = "1234567890"
        val name = "테스트 주식회사"

        every { businessPlaceHelper.throwIfExists(businessNumber) } throws ConflictException(
            com.kcd.tax.common.exception.ErrorCode.BUSINESS_ALREADY_EXISTS,
            "이미 존재하는 사업자번호입니다"
        )

        // When & Then
        assertThrows<ConflictException> {
            service.createBusinessPlace(businessNumber, name)
        }

        verify { businessPlaceHelper.throwIfExists(businessNumber) }
        verify(exactly = 0) { businessPlaceHelper.save(any()) }
    }

    @Test
    fun `모든 사업장을 조회할 수 있다`() {
        // Given
        val businessPlaces = listOf(
            BusinessPlace("1234567890", "테스트1"),
            BusinessPlace("0987654321", "테스트2")
        )

        every { businessPlaceHelper.findAll() } returns businessPlaces

        // When
        val results = service.getAllBusinessPlaces()

        // Then
        assertEquals(2, results.size)
        assertEquals("1234567890", results[0].businessNumber)
        assertEquals("0987654321", results[1].businessNumber)

        verify { businessPlaceHelper.findAll() }
    }

    @Test
    fun `사업자번호로 사업장을 조회할 수 있다`() {
        // Given
        val businessNumber = "1234567890"
        val businessPlace = BusinessPlace(businessNumber, "테스트 주식회사")

        every { businessPlaceHelper.findByIdOrThrow(businessNumber) } returns businessPlace

        // When
        val result = service.getBusinessPlace(businessNumber)

        // Then
        assertEquals(businessNumber, result.businessNumber)
        assertEquals("테스트 주식회사", result.name)

        verify { businessPlaceHelper.findByIdOrThrow(businessNumber) }
    }

    @Test
    fun `존재하지 않는 사업장 조회 시 예외가 발생한다`() {
        // Given
        val businessNumber = "9999999999"
        every { businessPlaceHelper.findByIdOrThrow(businessNumber) } throws NotFoundException(
            com.kcd.tax.common.exception.ErrorCode.BUSINESS_NOT_FOUND,
            "사업장을 찾을 수 없습니다"
        )

        // When & Then
        assertThrows<NotFoundException> {
            service.getBusinessPlace(businessNumber)
        }

        verify { businessPlaceHelper.findByIdOrThrow(businessNumber) }
    }

    @Test
    fun `사업장명을 수정할 수 있다`() {
        // Given
        val businessNumber = "1234567890"
        val businessPlace = BusinessPlace(businessNumber, "원래 이름")
        val newName = "수정된 이름"

        every { businessPlaceHelper.findByIdOrThrow(businessNumber) } returns businessPlace
        every { businessPlaceHelper.save(any()) } answers { firstArg() }

        // When
        val result = service.updateBusinessPlace(businessNumber, newName)

        // Then
        assertEquals(newName, result.name)
        assertEquals(newName, businessPlace.name)

        verify { businessPlaceHelper.findByIdOrThrow(businessNumber) }
        verify { businessPlaceHelper.save(businessPlace) }
    }

    @Test
    fun `권한을 부여할 수 있다`() {
        // Given
        val businessNumber = "1234567890"
        val adminId = 2L
        val businessPlace = BusinessPlace(businessNumber, "테스트")
        val admin = Admin(adminId, "manager1", AdminRole.MANAGER)
        val slot = slot<BusinessPlaceAdmin>()

        every { businessPlaceHelper.findByIdOrThrow(businessNumber) } returns businessPlace
        every { adminRepository.findById(adminId) } returns Optional.of(admin)
        every { businessPlaceAdminRepository.existsByBusinessNumberAndAdminId(businessNumber, adminId) } returns false
        every { businessPlaceAdminRepository.save(capture(slot)) } answers {
            val captured = slot.captured
            BusinessPlaceAdmin(1L, captured.businessNumber, captured.adminId)
        }

        // When
        val result = service.grantPermission(businessNumber, adminId)

        // Then
        assertEquals(businessNumber, slot.captured.businessNumber)
        assertEquals(adminId, slot.captured.adminId)
        assertEquals(businessNumber, result.businessNumber)
        assertEquals(adminId, result.adminId)
        assertEquals("manager1", result.adminUsername)

        verify { businessPlaceHelper.findByIdOrThrow(businessNumber) }
        verify { adminRepository.findById(adminId) }
        verify { businessPlaceAdminRepository.existsByBusinessNumberAndAdminId(businessNumber, adminId) }
        verify { businessPlaceAdminRepository.save(any()) }
    }

    @Test
    fun `이미 권한이 있는 경우 예외가 발생한다`() {
        // Given
        val businessNumber = "1234567890"
        val adminId = 2L
        val businessPlace = BusinessPlace(businessNumber, "테스트")
        val admin = Admin(adminId, "manager1", AdminRole.MANAGER)

        every { businessPlaceHelper.findByIdOrThrow(businessNumber) } returns businessPlace
        every { adminRepository.findById(adminId) } returns Optional.of(admin)
        every { businessPlaceAdminRepository.existsByBusinessNumberAndAdminId(businessNumber, adminId) } returns true

        // When & Then
        assertThrows<ConflictException> {
            service.grantPermission(businessNumber, adminId)
        }

        verify(exactly = 0) { businessPlaceAdminRepository.save(any()) }
    }

    @Test
    fun `권한을 해제할 수 있다`() {
        // Given
        val businessNumber = "1234567890"
        val adminId = 2L

        every { businessPlaceAdminRepository.existsByBusinessNumberAndAdminId(businessNumber, adminId) } returns true
        every { businessPlaceAdminRepository.deleteByBusinessNumberAndAdminId(businessNumber, adminId) } returns 1

        // When
        service.revokePermission(businessNumber, adminId)

        // Then
        verify { businessPlaceAdminRepository.existsByBusinessNumberAndAdminId(businessNumber, adminId) }
        verify { businessPlaceAdminRepository.deleteByBusinessNumberAndAdminId(businessNumber, adminId) }
    }

    @Test
    fun `권한이 없는 경우 해제 시 예외가 발생한다`() {
        // Given
        val businessNumber = "1234567890"
        val adminId = 2L

        every { businessPlaceAdminRepository.existsByBusinessNumberAndAdminId(businessNumber, adminId) } returns false

        // When & Then
        assertThrows<NotFoundException> {
            service.revokePermission(businessNumber, adminId)
        }

        verify { businessPlaceAdminRepository.existsByBusinessNumberAndAdminId(businessNumber, adminId) }
        verify(exactly = 0) { businessPlaceAdminRepository.deleteByBusinessNumberAndAdminId(any(), any()) }
    }

    @Test
    fun `사업장의 모든 권한 목록을 조회할 수 있다`() {
        // Given
        val businessNumber = "1234567890"
        val businessPlace = BusinessPlace(businessNumber, "테스트")
        val details = listOf(
            BusinessPlaceAdminDetail(1L, businessNumber, 2L, "manager1", "MANAGER", LocalDateTime.now()),
            BusinessPlaceAdminDetail(2L, businessNumber, 3L, "manager2", "MANAGER", LocalDateTime.now())
        )

        every { businessPlaceHelper.findByIdOrThrow(businessNumber) } returns businessPlace
        every { businessPlaceAdminRepository.findDetailsByBusinessNumber(businessNumber) } returns details

        // When
        val results = service.getPermissionsByBusinessNumber(businessNumber)

        // Then
        assertEquals(2, results.size)
        assertEquals("manager1", results[0].adminUsername)
        assertEquals("manager2", results[1].adminUsername)
        assertEquals(2L, results[0].adminId)
        assertEquals(3L, results[1].adminId)

        verify { businessPlaceHelper.findByIdOrThrow(businessNumber) }
        verify { businessPlaceAdminRepository.findDetailsByBusinessNumber(businessNumber) }
    }
}
