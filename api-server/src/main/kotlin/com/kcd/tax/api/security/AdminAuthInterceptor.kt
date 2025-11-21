package com.kcd.tax.api.security

import com.kcd.tax.common.enums.AdminRole
import com.kcd.tax.common.exception.ForbiddenException
import com.kcd.tax.common.exception.UnauthorizedException
import com.kcd.tax.api.security.annotation.RequireAdmin
import com.kcd.tax.api.security.annotation.RequireAuth
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor

/**
 * 관리자 인증 인터셉터
 *
 * HTTP 헤더에서 관리자 정보를 추출하여 AuthContext에 저장
 * - X-Admin-Id: 관리자 ID
 * - X-Admin-Role: 관리자 역할 (ADMIN/MANAGER)
 */
@Component
class AdminAuthInterceptor : HandlerInterceptor {

    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        const val HEADER_ADMIN_ID = "X-Admin-Id"
        const val HEADER_ADMIN_ROLE = "X-Admin-Role"
    }

    /**
     * 요청 전처리: 인증 정보 추출 및 권한 체크
     */
    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any
    ): Boolean {
        // HandlerMethod가 아닌 경우 (정적 리소스 등) 통과
        if (handler !is HandlerMethod) {
            return true
        }

        // @RequireAuth 어노테이션 체크 (메서드 또는 클래스 레벨)
        val requireAuth = handler.getMethodAnnotation(RequireAuth::class.java)
            ?: handler.beanType.getAnnotation(RequireAuth::class.java)

        // 인증이 필요없는 경우 통과
        if (requireAuth == null) {
            return true
        }

        // 헤더에서 인증 정보 추출
        val adminIdHeader = request.getHeader(HEADER_ADMIN_ID)
            ?: throw UnauthorizedException("$HEADER_ADMIN_ID 헤더가 필요합니다.")

        val adminRoleHeader = request.getHeader(HEADER_ADMIN_ROLE)
            ?: throw UnauthorizedException("$HEADER_ADMIN_ROLE 헤더가 필요합니다.")

        // Admin ID 파싱
        val adminId = adminIdHeader.toLongOrNull()
            ?: throw UnauthorizedException("유효하지 않은 Admin ID입니다: $adminIdHeader")

        // Admin Role 파싱
        val adminRole = try {
            AdminRole.valueOf(adminRoleHeader)
        } catch (e: IllegalArgumentException) {
            throw UnauthorizedException("유효하지 않은 Role입니다: $adminRoleHeader")
        }

        // AuthContext에 인증 정보 저장
        AuthContext.setAuth(adminId, adminRole)

        logger.debug("인증 정보 설정: adminId={}, role={}", adminId, adminRole)

        // @RequireAdmin 어노테이션 체크
        val requireAdmin = handler.getMethodAnnotation(RequireAdmin::class.java)
        if (requireAdmin != null && adminRole != AdminRole.ADMIN) {
            logger.warn("ADMIN 권한 필요: adminId=$adminId, role=$adminRole")
            throw ForbiddenException("ADMIN 권한이 필요합니다.")
        }

        return true
    }

    /**
     * 요청 완료 후: AuthContext 초기화 (메모리 누수 방지)
     */
    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        ex: Exception?
    ) {
        AuthContext.clear()
        logger.debug("인증 정보 초기화 완료")
    }
}
