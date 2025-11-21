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
    fun `NotFoundException은 BusinessException이다`() {
        val exception = NotFoundException(ErrorCode.BUSINESS_NOT_FOUND)
        assertTrue(exception is BusinessException)
    }

    @Test
    fun `ConflictException은 BusinessException이다`() {
        val exception = ConflictException(ErrorCode.COLLECTION_ALREADY_IN_PROGRESS)
        assertTrue(exception is BusinessException)
    }

    @Test
    fun `ForbiddenException은 BusinessException이다`() {
        val exception = ForbiddenException("권한이 없습니다")
        assertTrue(exception is BusinessException)
    }

    @Test
    fun `BadRequestException은 BusinessException이다`() {
        val exception = BadRequestException(ErrorCode.INVALID_INPUT)
        assertTrue(exception is BusinessException)
    }

    @Test
    fun `예외 메시지가 올바르게 설정된다`() {
        val message = "테스트 메시지"
        val exception = NotFoundException(ErrorCode.BUSINESS_NOT_FOUND, message)

        assertEquals(message, exception.message)
    }
}
