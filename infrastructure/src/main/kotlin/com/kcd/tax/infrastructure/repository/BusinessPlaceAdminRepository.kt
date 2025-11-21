package com.kcd.tax.infrastructure.repository

import com.kcd.tax.infrastructure.domain.BusinessPlaceAdmin
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

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
}
