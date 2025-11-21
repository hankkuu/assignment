package com.kcd.tax.api.controller.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/**
 * 사업장 생성 요청 DTO
 */
data class CreateBusinessPlaceRequest(
    @field:NotBlank(message = "사업자번호는 필수입니다.")
    @field:Pattern(
        regexp = "^\\d{10}$",
        message = "사업자번호는 10자리 숫자여야 합니다."
    )
    val businessNumber: String,

    @field:NotBlank(message = "사업장명은 필수입니다.")
    @field:Size(max = 100, message = "사업장명은 100자 이내여야 합니다.")
    val name: String
)
