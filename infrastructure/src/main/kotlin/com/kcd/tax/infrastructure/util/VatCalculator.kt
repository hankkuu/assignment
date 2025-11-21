package com.kcd.tax.infrastructure.util

import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 부가세 계산 유틸리티
 *
 * 계산 공식: (매출 - 매입) × 1/11
 * 반올림: 1의 자리에서 반올림하여 10원 단위로 처리
 * 예시: 12345.12 → 12350
 */
@Component
class VatCalculator {

    companion object {
        /**
         * 부가세율: 1/11 = 0.090909...
         */
        private val VAT_RATE = BigDecimal.ONE.divide(BigDecimal("11"), 20, RoundingMode.HALF_UP)

        /**
         * 10원 단위 처리를 위한 상수
         */
        private val TEN = BigDecimal.TEN
    }

    /**
     * 부가세 계산
     *
     * @param totalSales 총 매출
     * @param totalPurchases 총 매입
     * @return 부가세 (10원 단위)
     *
     * 계산 과정:
     * 1. (매출 - 매입) × 1/11 계산
     * 2. 소수점 첫째 자리에서 반올림
     * 3. 1의 자리에서 반올림하여 10의 자리로 맞춤
     *
     * 예시:
     * - (10,000,000 - 5,000,000) × 1/11 = 454,545.45...
     * - 소수점 반올림: 454,545
     * - 10원 단위 반올림: 454,550
     */
    fun calculate(totalSales: BigDecimal, totalPurchases: BigDecimal): Long {
        // 과세 표준액 계산
        val taxBase = totalSales.subtract(totalPurchases)

        // 부가세 = 과세표준 × 1/11
        val vat = taxBase.multiply(VAT_RATE)

        // 소수점 반올림
        val vatRounded = vat.setScale(0, RoundingMode.HALF_UP)

        // 1의 자리에서 반올림하여 10의 자리로 맞춤
        // 예: 12345 → 1234.5 → 1235 → 12350
        val result = vatRounded
            .divide(TEN, 1, RoundingMode.HALF_UP)  // 10으로 나누고 소수점 첫째자리까지
            .setScale(0, RoundingMode.HALF_UP)     // 반올림
            .multiply(TEN)                          // 다시 10을 곱함

        return result.toLong()
    }

    /**
     * 부가세 계산 (개별 금액 리스트로부터)
     */
    fun calculateFromAmounts(
        salesAmounts: List<BigDecimal>,
        purchaseAmounts: List<BigDecimal>
    ): Long {
        val totalSales = salesAmounts.fold(BigDecimal.ZERO, BigDecimal::add)
        val totalPurchases = purchaseAmounts.fold(BigDecimal.ZERO, BigDecimal::add)
        return calculate(totalSales, totalPurchases)
    }
}
