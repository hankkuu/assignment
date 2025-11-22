package com.kcd.tax.api.service

import com.kcd.tax.common.constants.ErrorMessages
import com.kcd.tax.common.enums.AdminRole
import com.kcd.tax.common.enums.TransactionType
import com.kcd.tax.common.exception.ErrorCode
import com.kcd.tax.common.exception.ForbiddenException
import com.kcd.tax.common.exception.NotFoundException
import com.kcd.tax.infrastructure.helper.BusinessPlaceRepositoryHelper
import com.kcd.tax.infrastructure.repository.BusinessPlaceAdminRepository
import com.kcd.tax.infrastructure.repository.TransactionRepository
import com.kcd.tax.infrastructure.repository.TransactionSumResult
import com.kcd.tax.infrastructure.util.VatCalculator
import com.kcd.tax.api.util.PageableHelper
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
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
     * 여러 사업장의 부가세 계산 with Pagination (권한 기반)
     *
     * 권한 필터링 후 페이징 처리하여 부가세를 계산합니다.
     *
     * @param adminId 관리자 ID
     * @param role 관리자 역할
     * @param businessNumber 특정 사업장 번호 (선택)
     * @param pageable 페이징 정보
     * @return 페이징된 부가세 계산 결과
     */
    fun calculateVatWithPaging(
        adminId: Long,
        role: AdminRole,
        businessNumber: String?,
        pageable: Pageable
    ): Page<VatResult> {
        logger.info("부가세 페이징 조회: adminId={}, role={}, businessNumber={}", adminId, role, businessNumber)

        // 1. 권한 기반 사업장 목록 조회
        val authorizedBusinessNumbers = when {
            businessNumber != null -> {
                // 특정 사업장 조회 시 권한 체크
                checkPermission(businessNumber, adminId, role)
                listOf(businessNumber)
            }
            else -> {
                // 전체 조회 (권한별)
                getAuthorizedBusinessNumbers(adminId, role)
            }
        }

        // 2. 페이징 적용 (메모리 기반)
        val pagedBusinessNumbers = PageableHelper.extractPagedItems(authorizedBusinessNumbers, pageable)

        // 3. 부가세 계산 (페이징된 사업장만)
        val results = if (pagedBusinessNumbers.isNotEmpty()) {
            calculateVat(pagedBusinessNumbers)
        } else {
            emptyList()
        }

        // 4. Page 객체 생성 (전체 개수는 authorizedBusinessNumbers 크기)
        return org.springframework.data.domain.PageImpl(
            results,
            pageable,
            authorizedBusinessNumbers.size.toLong()
        )
    }

    /**
     * 여러 사업장의 부가세 계산 (최적화: N+1 Query 방지)
     *
     * @param businessNumbers 사업자번호 목록
     * @return 부가세 계산 결과 목록
     */
    fun calculateVat(businessNumbers: List<String>): List<VatResult> {
        logger.info("부가세 계산 시작: ${businessNumbers.size}개 사업장")

        if (businessNumbers.isEmpty()) {
            return emptyList()
        }

        // 1. 사업장 정보 조회 (1 Query)
        val businessPlaces = businessPlaceHelper.findAllByIds(businessNumbers)
            .associateBy { it.businessNumber }

        // 1-1. 존재하지 않는 사업장 확인
        val missingBusinessNumbers = businessNumbers.filter { it !in businessPlaces.keys }
        if (missingBusinessNumbers.isNotEmpty()) {
            throw NotFoundException(
                ErrorCode.BUSINESS_NOT_FOUND,
                "사업장을 찾을 수 없습니다: ${missingBusinessNumbers.first()}"
            )
        }

        // 2. 매출 합계 조회 (1 Query) - Type-safe DTO
        val salesMap = transactionRepository
            .sumAmountByBusinessNumbersAndType(businessNumbers, TransactionType.SALES)
            .associate { it.businessNumber to it.totalAmount }

        // 3. 매입 합계 조회 (1 Query) - Type-safe DTO
        val purchasesMap = transactionRepository
            .sumAmountByBusinessNumbersAndType(businessNumbers, TransactionType.PURCHASE)
            .associate { it.businessNumber to it.totalAmount }

        // 4. 부가세 계산 (메모리 연산)
        return businessNumbers.map { businessNumber ->
            val businessPlace = businessPlaces[businessNumber]
                ?: error("사업장을 찾을 수 없습니다: $businessNumber") // 위에서 검증했지만 안전성을 위해 명시적 처리
            val totalSales = salesMap[businessNumber] ?: BigDecimal.ZERO
            val totalPurchases = purchasesMap[businessNumber] ?: BigDecimal.ZERO
            val vatAmount = vatCalculator.calculate(totalSales, totalPurchases)

            VatResult(
                businessNumber = businessNumber,
                businessName = businessPlace.name,
                totalSales = totalSales,
                totalPurchases = totalPurchases,
                vatAmount = vatAmount
            )
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
