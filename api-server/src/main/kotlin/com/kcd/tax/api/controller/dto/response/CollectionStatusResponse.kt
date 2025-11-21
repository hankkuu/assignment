package com.kcd.tax.api.controller.dto.response

import com.kcd.tax.common.enums.CollectionStatus
import java.time.LocalDateTime

/**
 * 수집 상태 응답 DTO
 */
data class CollectionStatusResponse(
    val businessNumber: String,
    val status: CollectionStatus,
    val message: String? = null,
    val timestamp: LocalDateTime = LocalDateTime.now()
) {
    companion object {
        fun of(businessNumber: String, status: CollectionStatus, message: String? = null): CollectionStatusResponse {
            return CollectionStatusResponse(
                businessNumber = businessNumber,
                status = status,
                message = message
            )
        }
    }
}
