package com.kcd.tax.infrastructure.repository

import com.kcd.tax.infrastructure.domain.Transaction
import com.kcd.tax.common.enums.TransactionType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.math.BigDecimal

/**
 * 거래 금액 합계 조회 결과 DTO (Type-safe query result)
 */
data class TransactionSumResult(
    val businessNumber: String,
    val totalAmount: BigDecimal
)

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

    /**
     * 여러 사업장의 타입별 거래 금액 합계 조회 (N+1 Query 방지, Type-safe)
     *
     * @return List<TransactionSumResult> - 사업자번호와 합계금액의 타입 안전한 목록
     */
    @Query("""
        SELECT new com.kcd.tax.infrastructure.repository.TransactionSumResult(
            t.businessNumber,
            COALESCE(SUM(t.amount), 0)
        )
        FROM Transaction t
        WHERE t.businessNumber IN :businessNumbers
        AND t.type = :type
        GROUP BY t.businessNumber
    """)
    fun sumAmountByBusinessNumbersAndType(
        @Param("businessNumbers") businessNumbers: List<String>,
        @Param("type") type: TransactionType
    ): List<TransactionSumResult>
}
