package com.kcd.tax.api.controller

import com.kcd.tax.api.controller.dto.request.CreateBusinessPlaceRequest
import com.kcd.tax.api.controller.dto.request.GrantPermissionRequest
import com.kcd.tax.api.controller.dto.request.UpdateBusinessPlaceRequest
import com.kcd.tax.api.controller.dto.response.BusinessPlaceResponse
import com.kcd.tax.api.controller.dto.response.PermissionListResponse
import com.kcd.tax.api.controller.dto.response.PermissionResponse
import com.kcd.tax.api.security.annotation.RequireAdmin
import com.kcd.tax.api.security.annotation.RequireAuth
import com.kcd.tax.api.service.BusinessPlaceService
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * 사업장 관리 및 권한 관리 API Controller
 *
 * 사업장 CRUD:
 * - POST /api/v1/business-places: 사업장 생성 (ADMIN)
 * - GET /api/v1/business-places: 사업장 목록 조회 (ADMIN)
 * - GET /api/v1/business-places/{businessNumber}: 사업장 상세 조회
 * - PUT /api/v1/business-places/{businessNumber}: 사업장 정보 수정 (ADMIN)
 *
 * 권한 관리 (ADMIN 전용):
 * - POST /api/v1/business-places/{businessNumber}/admins: 권한 부여
 * - GET /api/v1/business-places/{businessNumber}/admins: 권한 목록 조회
 * - DELETE /api/v1/business-places/{businessNumber}/admins/{adminId}: 권한 삭제
 */
@RestController
@RequestMapping("/api/v1/business-places")
@RequireAuth
class BusinessPlaceController(
    private val businessPlaceService: BusinessPlaceService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

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
        logger.info("사업장 생성 API 호출: businessNumber=${request.businessNumber}, name=${request.name}")

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
        logger.info("사업장 목록 조회 API 호출")

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
        logger.info("사업장 상세 조회 API 호출: businessNumber=$businessNumber")

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
        logger.info("사업장 수정 API 호출: businessNumber=$businessNumber, name=${request.name}")

        val businessPlace = businessPlaceService.updateBusinessPlace(
            businessNumber = businessNumber,
            name = request.name
        )

        val response = BusinessPlaceResponse.from(businessPlace)

        return ResponseEntity.ok(response)
    }

    // ========================================
    // 사업장 권한 관리 (ADMIN 전용)
    // ========================================

    /**
     * 사업장에 관리자 권한 부여 (ADMIN 전용)
     *
     * @param businessNumber 사업자번호
     * @param request 관리자 ID
     * @return 권한 정보
     */
    @PostMapping("/{businessNumber}/admins")
    @RequireAdmin
    fun grantPermission(
        @PathVariable businessNumber: String,
        @Valid @RequestBody request: GrantPermissionRequest
    ): ResponseEntity<PermissionResponse> {
        logger.info("권한 부여 API 호출: businessNumber=$businessNumber, adminId=${request.adminId}")

        val permission = businessPlaceService.grantPermission(businessNumber, request.adminId)
        val response = PermissionResponse.from(permission)

        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    /**
     * 사업장의 관리자 권한 목록 조회 (ADMIN 전용)
     *
     * @param businessNumber 사업자번호
     * @return 권한 목록
     */
    @GetMapping("/{businessNumber}/admins")
    @RequireAdmin
    fun getPermissions(
        @PathVariable businessNumber: String
    ): ResponseEntity<PermissionListResponse> {
        logger.info("권한 목록 조회 API 호출: businessNumber=$businessNumber")

        val permissions = businessPlaceService.getPermissionsByBusinessNumber(businessNumber)
        val responses = permissions.map { PermissionResponse.from(it) }

        val response = PermissionListResponse(
            businessNumber = businessNumber,
            admins = responses
        )

        return ResponseEntity.ok(response)
    }

    /**
     * 사업장의 관리자 권한 삭제 (ADMIN 전용)
     *
     * @param businessNumber 사업자번호
     * @param adminId 관리자 ID
     */
    @DeleteMapping("/{businessNumber}/admins/{adminId}")
    @RequireAdmin
    fun revokePermission(
        @PathVariable businessNumber: String,
        @PathVariable adminId: Long
    ): ResponseEntity<Void> {
        logger.info("권한 삭제 API 호출: businessNumber=$businessNumber, adminId=$adminId")

        businessPlaceService.revokePermission(businessNumber, adminId)

        return ResponseEntity.noContent().build()
    }
}
