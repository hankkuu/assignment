package com.kcd.tax.api.security

import com.kcd.tax.common.enums.AdminRole
import com.kcd.tax.common.exception.UnauthorizedException

/**
 * 인증 컨텍스트 (ThreadLocal 기반)
 *
 * 현재 요청의 인증 정보를 저장하고 조회
 * Interceptor에서 설정하고 Controller/Service에서 사용
 */
object AuthContext {

    private val adminIdHolder = ThreadLocal<Long>()
    private val adminRoleHolder = ThreadLocal<AdminRole>()

    /**
     * 인증 정보 설정
     *
     * @param adminId 관리자 ID
     * @param role 관리자 역할
     */
    fun setAuth(adminId: Long, role: AdminRole) {
        adminIdHolder.set(adminId)
        adminRoleHolder.set(role)
    }

    /**
     * 현재 관리자 ID 조회
     *
     * @return 관리자 ID
     * @throws UnauthorizedException 인증 정보가 없는 경우
     */
    fun getAdminId(): Long {
        return adminIdHolder.get()
            ?: throw UnauthorizedException("인증 정보가 없습니다.")
    }

    /**
     * 현재 관리자 역할 조회
     *
     * @return 관리자 역할
     * @throws UnauthorizedException 인증 정보가 없는 경우
     */
    fun getAdminRole(): AdminRole {
        return adminRoleHolder.get()
            ?: throw UnauthorizedException("인증 정보가 없습니다.")
    }

    /**
     * 현재 관리자가 ADMIN 권한을 가지고 있는지 확인
     */
    fun isAdmin(): Boolean {
        return try {
            getAdminRole() == AdminRole.ADMIN
        } catch (e: UnauthorizedException) {
            false
        }
    }

    /**
     * 현재 관리자가 MANAGER 권한을 가지고 있는지 확인
     */
    fun isManager(): Boolean {
        return try {
            getAdminRole() == AdminRole.MANAGER
        } catch (e: UnauthorizedException) {
            false
        }
    }

    /**
     * 인증 정보 조회 (안전한 방식)
     *
     * @return 관리자 ID와 역할을 담은 Pair, 없으면 null
     */
    fun getAuthOrNull(): Pair<Long, AdminRole>? {
        val adminId = adminIdHolder.get()
        val role = adminRoleHolder.get()
        return if (adminId != null && role != null) {
            Pair(adminId, role)
        } else {
            null
        }
    }

    /**
     * 인증 정보 초기화
     * 요청 처리 완료 후 반드시 호출해야 메모리 누수 방지
     */
    fun clear() {
        adminIdHolder.remove()
        adminRoleHolder.remove()
    }
}
