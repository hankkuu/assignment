package com.kcd.tax.api.service

import com.kcd.tax.common.constants.ErrorMessages
import com.kcd.tax.common.enums.AdminRole
import com.kcd.tax.common.enums.TransactionType
import com.kcd.tax.common.exception.ForbiddenException
import com.kcd.tax.infrastructure.helper.BusinessPlaceRepositoryHelper
import com.kcd.tax.infrastructure.repository.BusinessPlaceAdminRepository
import com.kcd.tax.infrastructure.repository.TransactionRepository
import com.kcd.tax.infrastructure.util.VatCalculator
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

/**
 * 부가세 계산 서비스
 *
 * 사업장별 부가세 계산 및 권한 기반 조회 제공
 * 리팩토링: 중복 제거, 상수 사용, 헬퍼 활용
 */
@Service
@Transactional(readOnly = true)
class VatCalculationService(
    private val transactionRepository: TransactionRepository,
    private val businessPlaceHelper: BusinessPlaceRepositoryHelper,
    private val businessPlaceAdminRepository: BusinessPlaceAdminRepository,
    private val vatCalculator: VatCalculator
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 부가세 계산 결과 DTO
     */
    data class VatResult(
        val businessNumber: String,
        val businessName: String,
        val totalSales: BigDecimal,
        val totalPurchases: BigDecimal,
        val vatAmount: Long
    )

    /**
     * 여러 사업장의 부가세 계산
     *
     * @param businessNumbers 사업자번호 목록
     * @return 부가세 계산 결과 목록
     */
    fun calculateVat(businessNumbers: List<String>): List<VatResult> {
        logger.info("부가세 계산 시작: ${businessNumbers.size}개 사업장")

        return businessNumbers.map { businessNumber ->
            calculateVatForBusinessPlace(businessNumber)
        }
    }

    /**
     * 단일 사업장의 부가세 계산
     *
     * @param businessNumber 사업자번호
     * @return 부가세 계산 결과
     */
    fun calculateVatForBusinessPlace(businessNumber: String): VatResult {
        logger.debug("부가세 계산: businessNumber=$businessNumber")

        // 1. 사업장 조회
        val businessPlace = businessPlaceHelper.findByIdOrThrow(businessNumber)

        // 2. 매출 합계
        val totalSales = transactionRepository.sumAmountByBusinessNumberAndType(
            businessNumber,
            TransactionType.SALES
        )

        // 3. 매입 합계
        val totalPurchases = transactionRepository.sumAmountByBusinessNumberAndType(
            businessNumber,
            TransactionType.PURCHASE
        )

        // 4. 부가세 계산: (매출 - 매입) × 1/11, 10원 단위 반올림
        val vatAmount = vatCalculator.calculate(totalSales, totalPurchases)

        logger.debug(
            "부가세 계산 완료: businessNumber={}, sales={}, purchases={}, vat={}",
            businessNumber,
            totalSales,
            totalPurchases,
            vatAmount
        )

        return VatResult(
            businessNumber = businessNumber,
            businessName = businessPlace.name,
            totalSales = totalSales,
            totalPurchases = totalPurchases,
            vatAmount = vatAmount
        )
    }

    /**
     * 관리자가 접근 가능한 사업장 목록 조회 (권한 기반)
     *
     * @param adminId 관리자 ID
     * @param role 관리자 역할
     * @return 접근 가능한 사업장 번호 목록
     */
    fun getAuthorizedBusinessNumbers(adminId: Long, role: AdminRole): List<String> {
        return when (role) {
            AdminRole.ADMIN -> getAllBusinessNumbers()
            AdminRole.MANAGER -> getManagerBusinessNumbers(adminId)
        }
    }

    /**
     * 특정 사업장에 대한 접근 권한 확인
     *
     * @param businessNumber 사업자번호
     * @param adminId 관리자 ID
     * @param role 관리자 역할
     * @throws ForbiddenException 권한이 없는 경우
     */
    fun checkPermission(businessNumber: String, adminId: Long, role: AdminRole) {
        if (role == AdminRole.ADMIN) {
            // ADMIN은 모든 사업장 접근 가능
            return
        }

        // MANAGER는 할당된 사업장만 접근 가능
        checkManagerPermission(businessNumber, adminId)
    }

    // ========================================
    // Private Helper Methods
    // ========================================

    /**
     * 모든 사업장 번호 조회 (ADMIN용)
     */
    private fun getAllBusinessNumbers(): List<String> {
        logger.debug("ADMIN 권한: 전체 사업장 조회")
        return businessPlaceHelper.findAll().map { it.businessNumber }
    }

    /**
     * MANAGER가 접근 가능한 사업장 번호 조회
     */
    private fun getManagerBusinessNumbers(adminId: Long): List<String> {
        logger.debug("MANAGER 권한: 할당된 사업장만 조회, adminId=$adminId")
        return businessPlaceAdminRepository.findBusinessNumbersByAdminId(adminId)
    }

    /**
     * MANAGER의 사업장 접근 권한 확인
     */
    private fun checkManagerPermission(businessNumber: String, adminId: Long) {
        val hasPermission = businessPlaceAdminRepository.existsByBusinessNumberAndAdminId(
            businessNumber,
            adminId
        )

        if (!hasPermission) {
            logger.warn("권한 없음: businessNumber=$businessNumber, adminId=$adminId")
            throw ForbiddenException(ErrorMessages.PERMISSION_DENIED)
        }
    }
}
