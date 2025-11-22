package com.kcd.tax.api.controller

import com.kcd.tax.api.controller.dto.response.VatResponse
import com.kcd.tax.api.security.AuthContext
import com.kcd.tax.api.security.annotation.RequireAuth
import com.kcd.tax.api.service.VatCalculationService
import com.kcd.tax.common.exception.BadRequestException
import com.kcd.tax.common.exception.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
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
 *
 * 로깅은 ControllerLoggingAspect에서 AOP로 자동 처리
 * (단, 비즈니스 validation 경고는 개별 처리)
 */
@RestController
@RequestMapping("/api/v1/vat")
@RequireAuth
class VatController(
    private val vatCalculationService: VatCalculationService
) {
    // Validation 경고용 logger (일반 API 로깅은 AOP 처리)
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val MAX_PAGE_SIZE = 100
    }

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
        // Page size 제한 (DoS 공격 방지)
        if (pageable.pageSize > MAX_PAGE_SIZE) {
            logger.warn("페이지 크기 초과: size={}, max={}", pageable.pageSize, MAX_PAGE_SIZE)
            throw BadRequestException(ErrorCode.INVALID_INPUT, "페이지 크기는 최대 ${MAX_PAGE_SIZE}개까지 허용됩니다")
        }

        val adminId = AuthContext.getAdminId()
        val adminRole = AuthContext.getAdminRole()

        // Service 레이어에서 권한 체크 + 페이징 + 부가세 계산
        val resultPage = vatCalculationService.calculateVatWithPaging(
            adminId = adminId,
            role = adminRole,
            businessNumber = businessNumber,
            pageable = pageable
        )

        // DTO 변환
        val responsePage = resultPage.map { VatResponse.from(it) }

        return ResponseEntity.ok(responsePage)
    }
}
