package com.kcd.tax.api.controller.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

/**
 * 수집 요청 DTO
 */
data class CollectionRequest(
    @field:NotBlank(message = "사업자번호는 필수입니다.")
    @field:Pattern(
        regexp = "^\\d{10}$",
        message = "사업자번호는 10자리 숫자여야 합니다."
    )
    val businessNumber: String
)
