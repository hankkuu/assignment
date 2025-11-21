package com.kcd.tax.infrastructure.repository

import com.kcd.tax.infrastructure.domain.Admin
import com.kcd.tax.common.enums.AdminRole
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface AdminRepository : JpaRepository<Admin, Long> {

    /**
     * 사용자명으로 관리자 조회
     */
    fun findByUsername(username: String): Optional<Admin>

    /**
     * 역할별 관리자 조회
     */
    fun findByRole(role: AdminRole): List<Admin>

    /**
     * 사용자명 존재 여부 확인
     */
    fun existsByUsername(username: String): Boolean
}
