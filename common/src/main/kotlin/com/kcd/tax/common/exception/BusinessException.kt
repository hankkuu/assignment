package com.kcd.tax.common.exception

/**
 * 비즈니스 로직 예외
 */
open class BusinessException(
    val errorCode: ErrorCode,
    override val message: String = errorCode.message,
    override val cause: Throwable? = null
) : RuntimeException(message, cause)

/**
 * 리소스를 찾을 수 없는 예외 (404)
 */
class NotFoundException(
    errorCode: ErrorCode,
    message: String = errorCode.message
) : BusinessException(errorCode, message)

/**
 * 인증 실패 예외 (401)
 */
class UnauthorizedException(
    message: String = ErrorCode.UNAUTHORIZED.message
) : BusinessException(ErrorCode.UNAUTHORIZED, message)

/**
 * 권한 없음 예외 (403)
 */
class ForbiddenException(
    message: String = ErrorCode.FORBIDDEN.message
) : BusinessException(ErrorCode.FORBIDDEN, message)

/**
 * 잘못된 요청 예외 (400)
 */
class BadRequestException(
    errorCode: ErrorCode,
    message: String = errorCode.message
) : BusinessException(errorCode, message)

/**
 * 충돌 예외 (409) - 이미 존재하는 리소스
 */
class ConflictException(
    errorCode: ErrorCode,
    message: String = errorCode.message
) : BusinessException(errorCode, message)
