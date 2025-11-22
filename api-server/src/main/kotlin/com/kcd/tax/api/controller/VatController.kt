package com.kcd.tax.api.controller

import com.kcd.tax.api.controller.dto.response.VatResponse
import com.kcd.tax.api.security.AuthContext
import com.kcd.tax.api.security.annotation.RequireAuth
import com.kcd.tax.api.service.VatCalculationService
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 부가세 조회 API Controller
 *
 * - GET /api/v1/vat: 부가세 조회
 *   - ADMIN: 전체 사업장 조회 가능
 *   - MANAGER: 권한이 부여된 사업장만 조회 가능
 *   - businessNumber 파라미터로 특정 사업장 조회 가능
 */
@RestController
@RequestMapping("/api/v1/vat")
@RequireAuth
class VatController(
    private val vatCalculationService: VatCalculationService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 부가세 조회 (Pagination 지원)
     *
     * @param businessNumber 사업자번호 (선택, 없으면 권한에 따라 전체 또는 할당된 사업장)
     * @param pageable 페이지 정보 (기본값: page=0, size=20)
     * @return 부가세 계산 결과 페이지
     */
    @GetMapping
    fun getVat(
        @RequestParam(required = false) businessNumber: String?,
        @PageableDefault(size = 20) pageable: Pageable
    ): ResponseEntity<Page<VatResponse>> {
        val adminId = AuthContext.getAdminId()
        val adminRole = AuthContext.getAdminRole()

        logger.info("부가세 조회 API 호출: adminId=$adminId, role=$adminRole, businessNumber=$businessNumber, page=${pageable.pageNumber}, size=${pageable.pageSize}")

        val businessNumbers = when {
            // 특정 사업장 조회
            businessNumber != null -> {
                // 권한 체크
                vatCalculationService.checkPermission(businessNumber, adminId, adminRole)
                listOf(businessNumber)
            }
            // 전체 조회 (권한별)
            else -> {
                vatCalculationService.getAuthorizedBusinessNumbers(adminId, adminRole)
            }
        }

        // Pagination 적용
        val totalElements = businessNumbers.size
        val start = (pageable.pageNumber * pageable.pageSize).coerceAtMost(totalElements)
        val end = (start + pageable.pageSize).coerceAtMost(totalElements)
        val pagedBusinessNumbers = if (start < totalElements) {
            businessNumbers.subList(start, end)
        } else {
            emptyList()
        }

        val results = vatCalculationService.calculateVat(pagedBusinessNumbers)
        val responses = results.map { VatResponse.from(it) }
        val page = PageImpl(responses, pageable, totalElements.toLong())

        logger.info("부가세 조회 완료: 전체 ${totalElements}개 중 ${responses.size}개 조회 (page ${pageable.pageNumber})")

        return ResponseEntity.ok(page)
    }
}
