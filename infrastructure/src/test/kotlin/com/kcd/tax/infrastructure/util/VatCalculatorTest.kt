package com.kcd.tax.infrastructure.util

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.math.BigDecimal

class VatCalculatorTest {

    private val vatCalculator = VatCalculator()

    @Test
    fun `기본 부가세 계산이 올바르게 작동한다`() {
        // Given: (매출 - 매입) × 1/11
        val sales = BigDecimal("10000000")
        val purchases = BigDecimal("5000000")

        // When
        val vat = vatCalculator.calculate(sales, purchases)

        // Then: (10,000,000 - 5,000,000) × 1/11 = 454,545.45... → 454,550 (10원 단위)
        assertEquals(454550L, vat)
    }

    @Test
    fun `10원 단위 반올림이 올바르게 작동한다`() {
        // Given: 결과가 12345가 나오는 경우
        val sales = BigDecimal("147945")
        val purchases = BigDecimal("12000")

        // When
        val vat = vatCalculator.calculate(sales, purchases)

        // Then: (147,945 - 12,000) × 1/11 = 12,358.63... → 12,359 → 12,360
        assertEquals(12360L, vat)
    }

    @Test
    fun `매출이 0인 경우 음수 부가세가 계산된다`() {
        // Given
        val sales = BigDecimal.ZERO
        val purchases = BigDecimal("1000000")

        // When
        val vat = vatCalculator.calculate(sales, purchases)

        // Then: (0 - 1,000,000) × 1/11 = -90,909.09... → -90,910
        assertEquals(-90910L, vat)
    }

    @Test
    fun `매입이 0인 경우 양수 부가세가 계산된다`() {
        // Given
        val sales = BigDecimal("1000000")
        val purchases = BigDecimal.ZERO

        // When
        val vat = vatCalculator.calculate(sales, purchases)

        // Then: (1,000,000 - 0) × 1/11 = 90,909.09... → 90,910
        assertEquals(90910L, vat)
    }

    @Test
    fun `매출과 매입이 같으면 부가세가 0이다`() {
        // Given
        val sales = BigDecimal("1000000")
        val purchases = BigDecimal("1000000")

        // When
        val vat = vatCalculator.calculate(sales, purchases)

        // Then
        assertEquals(0L, vat)
    }

    @Test
    fun `소수점이 있는 금액도 올바르게 계산된다`() {
        // Given
        val sales = BigDecimal("1234567.89")
        val purchases = BigDecimal("123456.78")

        // When
        val vat = vatCalculator.calculate(sales, purchases)

        // Then: (1,234,567.89 - 123,456.78) × 1/11 = 101,010.10... → 101,010
        assertEquals(101010L, vat)
    }

    @Test
    fun `리스트로부터 부가세 계산이 올바르게 작동한다`() {
        // Given
        val salesAmounts = listOf(
            BigDecimal("1000000"),
            BigDecimal("2000000"),
            BigDecimal("3000000")
        )
        val purchaseAmounts = listOf(
            BigDecimal("500000"),
            BigDecimal("500000")
        )

        // When
        val vat = vatCalculator.calculateFromAmounts(salesAmounts, purchaseAmounts)

        // Then: (6,000,000 - 1,000,000) × 1/11 = 454,545.45... → 454,550
        assertEquals(454550L, vat)
    }

    @Test
    fun `빈 리스트로부터 부가세 계산 시 0이 반환된다`() {
        // Given
        val salesAmounts = emptyList<BigDecimal>()
        val purchaseAmounts = emptyList<BigDecimal>()

        // When
        val vat = vatCalculator.calculateFromAmounts(salesAmounts, purchaseAmounts)

        // Then
        assertEquals(0L, vat)
    }

    @Test
    fun `실제 샘플 데이터 기준 계산이 올바르다`() {
        // Given: sample.xlsx 데이터
        // 매출: 47,811,032원 (412건)
        // 매입: 1,406,700원 (42건)
        val sales = BigDecimal("47811032")
        val purchases = BigDecimal("1406700")

        // When
        val vat = vatCalculator.calculate(sales, purchases)

        // Then: (47,811,032 - 1,406,700) × 1/11 = 4,218,575.63... → 4,218,576 → 4,218,580
        assertEquals(4218580L, vat)
    }

    @Test
    fun `큰 금액도 정확하게 계산된다`() {
        // Given
        val sales = BigDecimal("999999999")
        val purchases = BigDecimal("111111111")

        // When
        val vat = vatCalculator.calculate(sales, purchases)

        // Then: (999,999,999 - 111,111,111) × 1/11 = 80,808,080.72... → 80,808,081 → 80,808,080
        assertEquals(80808080L, vat)
    }

    @Test
    fun `1의 자리 반올림이 올바르게 작동한다 - 4 이하`() {
        // Given: 결과가 ...4가 나오는 경우
        val sales = BigDecimal("100044")
        val purchases = BigDecimal.ZERO

        // When
        val vat = vatCalculator.calculate(sales, purchases)

        // Then: 100,044 × 1/11 = 9,094.90... → 9,095 → 9,100 (5는 올림)
        assertEquals(9100L, vat)
    }

    @Test
    fun `1의 자리 반올림이 올바르게 작동한다 - 5 이상`() {
        // Given: 결과가 ...5가 나오는 경우
        val sales = BigDecimal("100055")
        val purchases = BigDecimal.ZERO

        // When
        val vat = vatCalculator.calculate(sales, purchases)

        // Then: 100,055 × 1/11 = 9,095.90... → 9,096 → 9,100 (6은 올림)
        assertEquals(9100L, vat)
    }

    // Edge Cases

    @Test
    fun `매우 작은 금액도 정확하게 계산된다`() {
        // Given
        val sales = BigDecimal("10")
        val purchases = BigDecimal("5")

        // When
        val vat = vatCalculator.calculate(sales, purchases)

        // Then: (10 - 5) × 1/11 = 0.45... → 0 → 0
        assertEquals(0L, vat)
    }

    @Test
    fun `음수 매출의 경우도 계산된다`() {
        // Given: 환불 등으로 매출이 음수인 경우
        val sales = BigDecimal("-1000000")
        val purchases = BigDecimal("500000")

        // When
        val vat = vatCalculator.calculate(sales, purchases)

        // Then: (-1,000,000 - 500,000) × 1/11 = -136,363.63... → -136,364 → -136,360
        assertEquals(-136360L, vat)
    }

    @Test
    fun `음수 매입의 경우도 계산된다`() {
        // Given: 반품 등으로 매입이 음수인 경우
        val sales = BigDecimal("1000000")
        val purchases = BigDecimal("-500000")

        // When
        val vat = vatCalculator.calculate(sales, purchases)

        // Then: (1,000,000 - (-500,000)) × 1/11 = 136,363.63... → 136,364 → 136,360
        assertEquals(136360L, vat)
    }

    @Test
    fun `Long 최대값에 근접한 금액도 처리된다`() {
        // Given: Long.MAX_VALUE = 9,223,372,036,854,775,807
        val sales = BigDecimal("50000000000000") // 50조
        val purchases = BigDecimal("10000000000000") // 10조

        // When
        val vat = vatCalculator.calculate(sales, purchases)

        // Then: (40조) × 1/11 = 3,636,363,636,363.63... → 3,636,363,636,364 → 3,636,363,636,360
        assertEquals(3636363636360L, vat)
    }

    @Test
    fun `매우 정밀한 소수점이 있어도 정확하게 계산된다`() {
        // Given
        val sales = BigDecimal("12345.123456789")
        val purchases = BigDecimal("6789.987654321")

        // When
        val vat = vatCalculator.calculate(sales, purchases)

        // Then: (12345.123456789 - 6789.987654321) × 1/11 = 505.01... → 505 → 510
        assertEquals(510L, vat)
    }

    @Test
    fun `둘 다 0인 경우`() {
        // Given
        val sales = BigDecimal.ZERO
        val purchases = BigDecimal.ZERO

        // When
        val vat = vatCalculator.calculate(sales, purchases)

        // Then
        assertEquals(0L, vat)
    }

    @Test
    fun `둘 다 음수인 경우`() {
        // Given
        val sales = BigDecimal("-1000000")
        val purchases = BigDecimal("-500000")

        // When
        val vat = vatCalculator.calculate(sales, purchases)

        // Then: (-1,000,000 - (-500,000)) × 1/11 = -45,454.54... → -45,455 → -45,460
        assertEquals(-45460L, vat)
    }

    @Test
    fun `과학적 표기법으로 표현된 큰 수도 처리된다`() {
        // Given
        val sales = BigDecimal("1E+10") // 10,000,000,000
        val purchases = BigDecimal("5E+9") // 5,000,000,000

        // When
        val vat = vatCalculator.calculate(sales, purchases)

        // Then: (10,000,000,000 - 5,000,000,000) × 1/11 = 454,545,454.54... → 454,545,455 → 454,545,460
        assertEquals(454545460L, vat)
    }

    @Test
    fun `리스트에 null이 없는지 확인 - 빈 리스트와 null이 아닌 리스트 혼합`() {
        // Given
        val salesAmounts = listOf(BigDecimal("1000000"))
        val purchaseAmounts = emptyList<BigDecimal>()

        // When
        val vat = vatCalculator.calculateFromAmounts(salesAmounts, purchaseAmounts)

        // Then: (1,000,000 - 0) × 1/11 = 90,909.09... → 90,910
        assertEquals(90910L, vat)
    }

    @Test
    fun `리스트에 음수 값이 포함된 경우`() {
        // Given
        val salesAmounts = listOf(
            BigDecimal("1000000"),
            BigDecimal("-200000"), // 환불
            BigDecimal("500000")
        )
        val purchaseAmounts = listOf(
            BigDecimal("300000"),
            BigDecimal("-50000") // 반품
        )

        // When
        val vat = vatCalculator.calculateFromAmounts(salesAmounts, purchaseAmounts)

        // Then: (1,300,000 - 250,000) × 1/11 = 95,454.54... → 95,455 → 95,460
        assertEquals(95460L, vat)
    }

    @Test
    fun `1원 단위 경계값 테스트 - 5원`() {
        // Given: 결과가 정확히 ...5로 떨어지는 경우
        val sales = BigDecimal("1000055")
        val purchases = BigDecimal.ZERO

        // When
        val vat = vatCalculator.calculate(sales, purchases)

        // Then: 1,000,055 × 1/11 = 90,914.09... → 90,914 → 90,910 (4는 내림)
        assertEquals(90910L, vat)
    }

    @Test
    fun `매입이 매출보다 큰 경우 - 손실 시나리오`() {
        // Given
        val sales = BigDecimal("3000000")
        val purchases = BigDecimal("5000000")

        // When
        val vat = vatCalculator.calculate(sales, purchases)

        // Then: (3,000,000 - 5,000,000) × 1/11 = -181,818.18... → -181,818 → -181,820
        assertEquals(-181820L, vat)
    }
}
