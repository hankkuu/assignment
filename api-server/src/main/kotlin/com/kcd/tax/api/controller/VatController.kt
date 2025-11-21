package com.kcd.tax.api.controller

import com.kcd.tax.api.controller.dto.response.VatResponse
import com.kcd.tax.api.security.AuthContext
import com.kcd.tax.api.security.annotation.RequireAuth
import com.kcd.tax.api.service.VatCalculationService
import org.slf4j.LoggerFactory
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
     * 부가세 조회
     *
     * @param businessNumber 사업자번호 (선택, 없으면 권한에 따라 전체 또는 할당된 사업장)
     * @return 부가세 계산 결과 목록
     */
    @GetMapping
    fun getVat(
        @RequestParam(required = false) businessNumber: String?
    ): ResponseEntity<List<VatResponse>> {
        val adminId = AuthContext.getAdminId()
        val adminRole = AuthContext.getAdminRole()

        logger.info("부가세 조회 API 호출: adminId=$adminId, role=$adminRole, businessNumber=$businessNumber")

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

        val results = vatCalculationService.calculateVat(businessNumbers)
        val responses = results.map { VatResponse.from(it) }

        logger.info("부가세 조회 완료: ${responses.size}개 사업장")

        return ResponseEntity.ok(responses)
    }
}
