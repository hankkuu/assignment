package com.kcd.tax.api.controller

import com.kcd.tax.api.controller.dto.request.GrantPermissionRequest
import com.kcd.tax.api.controller.dto.response.PermissionListResponse
import com.kcd.tax.api.controller.dto.response.PermissionResponse
import com.kcd.tax.api.security.annotation.RequireAdmin
import com.kcd.tax.api.security.annotation.RequireAuth
import com.kcd.tax.api.service.BusinessPlaceService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * 사업장-관리자 권한 관리 API Controller
 *
 * ADMIN 전용 기능:
 * - POST /api/v1/business-places/{businessNumber}/admins: 권한 부여
 * - GET /api/v1/business-places/{businessNumber}/admins: 권한 목록 조회
 * - DELETE /api/v1/business-places/{businessNumber}/admins/{adminId}: 권한 삭제
 *
 * 분리 이유:
 * - Single Responsibility Principle 준수
 * - 사업장 CRUD와 권한 관리는 별도의 관심사
 * - RESTful 서브 리소스 패턴 적용
 *
 * 로깅은 ControllerLoggingAspect에서 AOP로 자동 처리
 */
@RestController
@RequestMapping("/api/v1/business-places/{businessNumber}/admins")
@RequireAuth
class BusinessPlaceAdminController(
    private val businessPlaceService: BusinessPlaceService
) {
    /**
     * 사업장에 관리자 권한 부여
     *
     * @param businessNumber 사업자번호
     * @param request 관리자 ID
     * @return 권한 정보
     */
    @PostMapping
    @RequireAdmin
    fun grantPermission(
        @PathVariable businessNumber: String,
        @Valid @RequestBody request: GrantPermissionRequest
    ): ResponseEntity<PermissionResponse> {
        val permission = businessPlaceService.grantPermission(businessNumber, request.adminId)
        val response = PermissionResponse.from(permission)

        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    /**
     * 사업장의 관리자 권한 목록 조회
     *
     * @param businessNumber 사업자번호
     * @return 권한 목록
     */
    @GetMapping
    @RequireAdmin
    fun getPermissions(
        @PathVariable businessNumber: String
    ): ResponseEntity<PermissionListResponse> {
        val permissions = businessPlaceService.getPermissionsByBusinessNumber(businessNumber)
        val responses = permissions.map { PermissionResponse.from(it) }

        val response = PermissionListResponse(
            businessNumber = businessNumber,
            admins = responses
        )

        return ResponseEntity.ok(response)
    }

    /**
     * 사업장의 관리자 권한 삭제
     *
     * @param businessNumber 사업자번호
     * @param adminId 관리자 ID
     */
    @DeleteMapping("/{adminId}")
    @RequireAdmin
    fun revokePermission(
        @PathVariable businessNumber: String,
        @PathVariable adminId: Long
    ): ResponseEntity<Void> {
        businessPlaceService.revokePermission(businessNumber, adminId)

        return ResponseEntity.noContent().build()
    }
}
