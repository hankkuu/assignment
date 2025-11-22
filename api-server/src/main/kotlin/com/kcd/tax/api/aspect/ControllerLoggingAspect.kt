package com.kcd.tax.api.aspect

import jakarta.servlet.http.HttpServletRequest
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.*
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

/**
 * Controller 공통 로깅 Aspect
 *
 * 모든 REST API 호출에 대해 다음 정보를 자동으로 로깅:
 * - 요청 정보: HTTP Method, URI, Parameters
 * - 응답 정보: HTTP Status, 실행 시간
 * - 에러 정보: 예외 타입 및 메시지
 *
 * 로그 형식:
 * - [API_REQUEST] GET /api/v1/vat?businessNumber=1234567890
 * - [API_RESPONSE] GET /api/v1/vat status=200 duration=25ms
 * - [API_ERROR] POST /api/v1/collections error=ConflictException duration=5ms
 */
@Aspect
@Component
class ControllerLoggingAspect {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Controller 메서드 실행 전후 로깅
     *
     * Pointcut: com.kcd.tax.api.controller 패키지 내의 모든 public 메서드
     */
    @Around("execution(public * com.kcd.tax.api.controller..*(..))")
    fun logApiCall(joinPoint: ProceedingJoinPoint): Any? {
        val startTime = System.currentTimeMillis()
        val request = getCurrentHttpRequest()
        val method = request?.method ?: "UNKNOWN"
        val uri = request?.requestURI ?: "UNKNOWN"
        val queryString = request?.queryString

        // 요청 로깅
        val requestLog = buildRequestLog(method, uri, queryString)
        logger.info(requestLog)

        return try {
            // 실제 메서드 실행
            val result = joinPoint.proceed()

            // 응답 로깅 (성공)
            val duration = System.currentTimeMillis() - startTime
            val status = extractStatusCode(result)
            val responseLog = buildResponseLog(method, uri, status, duration)
            logger.info(responseLog)

            result
        } catch (ex: Exception) {
            // 에러 로깅
            val duration = System.currentTimeMillis() - startTime
            val errorLog = buildErrorLog(method, uri, ex, duration)
            logger.error(errorLog)

            throw ex
        }
    }

    /**
     * 현재 HTTP 요청 가져오기
     */
    private fun getCurrentHttpRequest(): HttpServletRequest? {
        val requestAttributes = RequestContextHolder.getRequestAttributes()
        return (requestAttributes as? ServletRequestAttributes)?.request
    }

    /**
     * 요청 로그 생성
     */
    private fun buildRequestLog(method: String, uri: String, queryString: String?): String {
        return if (queryString.isNullOrBlank()) {
            "[API_REQUEST] $method $uri"
        } else {
            "[API_REQUEST] $method $uri?$queryString"
        }
    }

    /**
     * 응답 로그 생성
     */
    private fun buildResponseLog(method: String, uri: String, status: Int, duration: Long): String {
        return "[API_RESPONSE] $method $uri status=$status duration=${duration}ms"
    }

    /**
     * 에러 로그 생성
     */
    private fun buildErrorLog(method: String, uri: String, ex: Exception, duration: Long): String {
        val errorType = ex.javaClass.simpleName
        return "[API_ERROR] $method $uri error=$errorType duration=${duration}ms"
    }

    /**
     * ResponseEntity에서 HTTP Status Code 추출
     */
    private fun extractStatusCode(result: Any?): Int {
        return when (result) {
            is ResponseEntity<*> -> result.statusCode.value()
            else -> 200 // 기본값
        }
    }
}
