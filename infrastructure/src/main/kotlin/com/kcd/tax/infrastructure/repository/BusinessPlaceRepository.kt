package com.kcd.tax.infrastructure.repository

import com.kcd.tax.infrastructure.domain.BusinessPlace
import com.kcd.tax.common.enums.CollectionStatus
import org.springframework.data.jpa.repository.JpaRepository
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
}
