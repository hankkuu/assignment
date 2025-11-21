package com.kcd.tax.api.controller.dto.response

import com.kcd.tax.infrastructure.domain.BusinessPlace
import java.time.LocalDateTime

/**
 * 사업장 응답 DTO
 */
data class BusinessPlaceResponse(
    val businessNumber: String,
    val name: String,
    val collectionStatus: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {
    companion object {
        fun from(businessPlace: BusinessPlace): BusinessPlaceResponse {
            return BusinessPlaceResponse(
                businessNumber = businessPlace.businessNumber,
                name = businessPlace.name,
                collectionStatus = businessPlace.collectionStatus.name,
                createdAt = businessPlace.createdAt,
                updatedAt = businessPlace.updatedAt
            )
        }
    }
}
