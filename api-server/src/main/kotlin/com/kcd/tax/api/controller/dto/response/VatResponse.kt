package com.kcd.tax.api.controller.dto.response

import com.kcd.tax.api.service.VatCalculationService
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 부가세 조회 응답 DTO
 */
data class VatResponse(
    val businessNumber: String,
    val businessName: String,
    val totalSales: BigDecimal,
    val totalPurchases: BigDecimal,
    val vatAmount: Long,
    val calculatedAt: LocalDateTime = LocalDateTime.now()
) {
    companion object {
        fun from(result: VatCalculationService.VatResult): VatResponse {
            return VatResponse(
                businessNumber = result.businessNumber,
                businessName = result.businessName,
                totalSales = result.totalSales,
                totalPurchases = result.totalPurchases,
                vatAmount = result.vatAmount
            )
        }
    }
}
