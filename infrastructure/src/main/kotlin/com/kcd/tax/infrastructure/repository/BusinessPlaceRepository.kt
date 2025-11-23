package com.kcd.tax.infrastructure.repository

import com.kcd.tax.common.enums.CollectionStatus
import com.kcd.tax.infrastructure.domain.BusinessPlace
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface BusinessPlaceRepository : JpaRepository<BusinessPlace, String> {

    /**
     * 수집 상태별 사업장 조회
     */
    fun findByCollectionStatus(status: CollectionStatus): List<BusinessPlace>

    /**
     * 사업장 존재 여부 확인
     */
    fun existsByBusinessNumber(businessNumber: String): Boolean

    /**
     * 수집 요청이 접수된 사업장 조회 (NOT_REQUESTED + requestedAt != null)
     */
    fun findByCollectionStatusAndCollectionRequestedAtIsNotNull(
        status: CollectionStatus
    ): List<BusinessPlace>

    /**
     * 비관적 락을 통한 사업장 조회 (수집 시작 시 동시성 제어)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM BusinessPlace b WHERE b.businessNumber = :businessNumber")
    fun findByBusinessNumberForUpdate(
        @Param("businessNumber") businessNumber: String
    ): BusinessPlace?
}
