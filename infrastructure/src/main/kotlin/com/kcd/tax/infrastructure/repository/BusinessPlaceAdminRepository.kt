package com.kcd.tax.infrastructure.repository

import com.kcd.tax.infrastructure.domain.BusinessPlaceAdmin
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

/**
 * 사업장-관리자 권한 정보 DTO (N+1 Query 방지용)
 */
data class BusinessPlaceAdminDetail(
    val permissionId: Long,
    val businessNumber: String,
    val adminId: Long,
    val adminName: String,
    val adminRole: String,
    val grantedAt: java.time.LocalDateTime
)

@Repository
interface BusinessPlaceAdminRepository : JpaRepository<BusinessPlaceAdmin, Long> {

    /**
     * 사업장-관리자 권한 존재 여부 확인
     */
    fun existsByBusinessNumberAndAdminId(
        businessNumber: String,
        adminId: Long
    ): Boolean

    /**
     * 사업장에 할당된 관리자 목록 조회
     */
    fun findByBusinessNumber(businessNumber: String): List<BusinessPlaceAdmin>

    /**
     * 관리자에게 할당된 사업장 목록 조회
     */
    fun findByAdminId(adminId: Long): List<BusinessPlaceAdmin>

    /**
     * 사업장-관리자 권한 삭제
     * @return 삭제된 레코드 수
     */
    fun deleteByBusinessNumberAndAdminId(
        businessNumber: String,
        adminId: Long
    ): Int

    /**
     * 관리자가 접근 가능한 사업장 번호 목록 조회
     */
    @Query("SELECT bpa.businessNumber FROM BusinessPlaceAdmin bpa WHERE bpa.adminId = :adminId")
    fun findBusinessNumbersByAdminId(adminId: Long): List<String>

    /**
     * 사업장의 관리자 권한 목록 조회 (Admin 정보 포함, N+1 Query 방지)
     *
     * JOIN을 사용하여 한 번의 쿼리로 BusinessPlaceAdmin과 Admin 정보를 모두 가져옴
     */
    @Query("""
        SELECT new com.kcd.tax.infrastructure.repository.BusinessPlaceAdminDetail(
            bpa.id,
            bpa.businessNumber,
            bpa.adminId,
            a.name,
            CAST(a.role AS string),
            bpa.grantedAt
        )
        FROM BusinessPlaceAdmin bpa
        INNER JOIN Admin a ON bpa.adminId = a.id
        WHERE bpa.businessNumber = :businessNumber
    """)
    fun findDetailsByBusinessNumber(businessNumber: String): List<BusinessPlaceAdminDetail>
}
