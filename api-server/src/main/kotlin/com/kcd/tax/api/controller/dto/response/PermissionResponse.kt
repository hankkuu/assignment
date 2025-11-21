package com.kcd.tax.api.controller.dto.response

import com.kcd.tax.api.service.BusinessPlaceService
import java.time.LocalDateTime

/**
 * 권한 정보 응답 DTO
 */
data class PermissionResponse(
    val id: Long?,
    val businessNumber: String,
    val adminId: Long,
    val adminUsername: String,
    val adminRole: String,
    val grantedAt: LocalDateTime
) {
    companion object {
        fun from(info: BusinessPlaceService.PermissionInfo): PermissionResponse {
            return PermissionResponse(
                id = info.id,
                businessNumber = info.businessNumber,
                adminId = info.adminId,
                adminUsername = info.adminUsername,
                adminRole = info.adminRole,
                grantedAt = info.grantedAt
            )
        }
    }
}

/**
 * 권한 목록 응답 DTO
 */
data class PermissionListResponse(
    val businessNumber: String,
    val admins: List<PermissionResponse>
)
