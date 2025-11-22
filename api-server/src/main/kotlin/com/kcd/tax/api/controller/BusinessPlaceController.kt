package com.kcd.tax.api.controller

import com.kcd.tax.api.controller.dto.request.CreateBusinessPlaceRequest
import com.kcd.tax.api.controller.dto.request.UpdateBusinessPlaceRequest
import com.kcd.tax.api.controller.dto.response.BusinessPlaceResponse
import com.kcd.tax.api.security.annotation.RequireAdmin
import com.kcd.tax.api.security.annotation.RequireAuth
import com.kcd.tax.api.service.BusinessPlaceService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * 사업장 관리 API Controller
 *
 * 사업장 CRUD:
 * - POST /api/v1/business-places: 사업장 생성 (ADMIN)
 * - GET /api/v1/business-places: 사업장 목록 조회 (ADMIN)
 * - GET /api/v1/business-places/{businessNumber}: 사업장 상세 조회
 * - PUT /api/v1/business-places/{businessNumber}: 사업장 정보 수정 (ADMIN)
 *
 * 권한 관리는 BusinessPlaceAdminController로 분리됨
 * 로깅은 ControllerLoggingAspect에서 AOP로 자동 처리
 */
@RestController
@RequestMapping("/api/v1/business-places")
@RequireAuth
class BusinessPlaceController(
    private val businessPlaceService: BusinessPlaceService
) {

    // ========================================
    // 사업장 CRUD
    // ========================================

    /**
     * 사업장 생성 (ADMIN 전용)
     *
     * @param request 사업장 생성 요청
     * @return 생성된 사업장 정보
     */
    @PostMapping
    @RequireAdmin
    fun createBusinessPlace(
        @Valid @RequestBody request: CreateBusinessPlaceRequest
    ): ResponseEntity<BusinessPlaceResponse> {
        val businessPlace = businessPlaceService.createBusinessPlace(
            businessNumber = request.businessNumber,
            name = request.name
        )

        val response = BusinessPlaceResponse.from(businessPlace)

        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    /**
     * 사업장 목록 조회 (ADMIN 전용)
     *
     * @return 전체 사업장 목록
     */
    @GetMapping
    @RequireAdmin
    fun getAllBusinessPlaces(): ResponseEntity<List<BusinessPlaceResponse>> {
        val businessPlaces = businessPlaceService.getAllBusinessPlaces()
        val responses = businessPlaces.map { BusinessPlaceResponse.from(it) }

        return ResponseEntity.ok(responses)
    }

    /**
     * 사업장 상세 조회
     *
     * @param businessNumber 사업자번호
     * @return 사업장 정보
     */
    @GetMapping("/{businessNumber}")
    fun getBusinessPlace(
        @PathVariable businessNumber: String
    ): ResponseEntity<BusinessPlaceResponse> {
        val businessPlace = businessPlaceService.getBusinessPlace(businessNumber)
        val response = BusinessPlaceResponse.from(businessPlace)

        return ResponseEntity.ok(response)
    }

    /**
     * 사업장 정보 수정 (ADMIN 전용)
     *
     * @param businessNumber 사업자번호
     * @param request 수정 요청
     * @return 수정된 사업장 정보
     */
    @PutMapping("/{businessNumber}")
    @RequireAdmin
    fun updateBusinessPlace(
        @PathVariable businessNumber: String,
        @Valid @RequestBody request: UpdateBusinessPlaceRequest
    ): ResponseEntity<BusinessPlaceResponse> {
        val businessPlace = businessPlaceService.updateBusinessPlace(
            businessNumber = businessNumber,
            name = request.name
        )

        val response = BusinessPlaceResponse.from(businessPlace)

        return ResponseEntity.ok(response)
    }
}
