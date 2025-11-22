package com.kcd.tax.common.exception

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class BusinessExceptionTest {

    @Test
    fun `ErrorCode로 예외를 생성할 수 있다`() {
        val exception = NotFoundException(ErrorCode.BUSINESS_NOT_FOUND)

        assertEquals(ErrorCode.BUSINESS_NOT_FOUND, exception.errorCode)
        assertEquals("BUSINESS_NOT_FOUND", exception.errorCode.name)
    }

    @Test
    fun `ErrorCode와 메시지로 예외를 생성할 수 있다`() {
        val customMessage = "사업장을 찾을 수 없습니다: 1234567890"
        val exception = NotFoundException(ErrorCode.BUSINESS_NOT_FOUND, customMessage)

        assertEquals(ErrorCode.BUSINESS_NOT_FOUND, exception.errorCode)
        assertEquals(customMessage, exception.message)
    }

    @Test
    fun `NotFoundException의 타입을 확인할 수 있다`() {
        val exception = NotFoundException(ErrorCode.BUSINESS_NOT_FOUND)
        assertEquals(NotFoundException::class, exception::class)
        assertEquals(ErrorCode.BUSINESS_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun `ConflictException의 타입을 확인할 수 있다`() {
        val exception = ConflictException(ErrorCode.COLLECTION_ALREADY_IN_PROGRESS)
        assertEquals(ConflictException::class, exception::class)
        assertEquals(ErrorCode.COLLECTION_ALREADY_IN_PROGRESS, exception.errorCode)
    }

    @Test
    fun `ForbiddenException의 타입을 확인할 수 있다`() {
        val exception = ForbiddenException("권한이 없습니다")
        assertEquals(ForbiddenException::class, exception::class)
        assertEquals("권한이 없습니다", exception.message)
    }

    @Test
    fun `BadRequestException의 타입을 확인할 수 있다`() {
        val exception = BadRequestException(ErrorCode.INVALID_INPUT)
        assertEquals(BadRequestException::class, exception::class)
        assertEquals(ErrorCode.INVALID_INPUT, exception.errorCode)
    }

    @Test
    fun `예외 메시지가 올바르게 설정된다`() {
        val message = "테스트 메시지"
        val exception = NotFoundException(ErrorCode.BUSINESS_NOT_FOUND, message)

        assertEquals(message, exception.message)
    }
}
