package com.kcd.tax.api.controller.dto.request

import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive

/**
 * 권한 부여 요청 DTO
 */
data class GrantPermissionRequest(
    @field:NotNull(message = "관리자 ID는 필수입니다.")
    @field:Positive(message = "관리자 ID는 양수여야 합니다.")
    val adminId: Long
)
