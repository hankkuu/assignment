package com.kcd.tax.infrastructure.repository

import com.kcd.tax.infrastructure.domain.Transaction
import com.kcd.tax.common.enums.TransactionType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.math.BigDecimal

@Repository
interface TransactionRepository : JpaRepository<Transaction, Long> {

    /**
     * 사업장별, 타입별 거래 금액 합계 조회
     */
    @Query("""
        SELECT COALESCE(SUM(t.amount), 0)
        FROM Transaction t
        WHERE t.businessNumber = :businessNumber
        AND t.type = :type
    """)
    fun sumAmountByBusinessNumberAndType(
        @Param("businessNumber") businessNumber: String,
        @Param("type") type: TransactionType
    ): BigDecimal

    /**
     * 사업장의 모든 거래 조회
     */
    fun findByBusinessNumber(businessNumber: String): List<Transaction>

    /**
     * 사업장의 거래 타입별 조회
     */
    fun findByBusinessNumberAndType(
        businessNumber: String,
        type: TransactionType
    ): List<Transaction>

    /**
     * 사업장의 모든 거래 삭제
     */
    fun deleteByBusinessNumber(businessNumber: String): Int
}
