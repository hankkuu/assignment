package com.kcd.tax.api.controller.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * 사업장 정보 수정 요청 DTO
 */
data class UpdateBusinessPlaceRequest(
    @field:NotBlank(message = "사업장명은 필수입니다.")
    @field:Size(max = 100, message = "사업장명은 100자 이내여야 합니다.")
    val name: String
)
