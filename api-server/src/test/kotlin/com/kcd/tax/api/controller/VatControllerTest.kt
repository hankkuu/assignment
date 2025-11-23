import com.kcd.tax.api.service.VatCalculationService
import com.kcd.tax.common.enums.AdminRole
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.math.BigDecimal

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    classes = [com.kcd.tax.api.TaxApiApplication::class]
)
@AutoConfigureMockMvc
class VatControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var vatCalculationService: VatCalculationService

    @Test
    fun `ADMIN은 모든 사업장의 부가세를 조회할 수 있다`() {
        // Given
        val adminId = 1L
        val vatResults = listOf(
            VatCalculationService.VatResult("1234567890", "테스트1", BigDecimal("10000000"), BigDecimal("5000000"), 454550L),
            VatCalculationService.VatResult("0987654321", "테스트2", BigDecimal("20000000"), BigDecimal("10000000"), 909090L)
        )
        val page = PageImpl(vatResults)

        every { vatCalculationService.calculateVatWithPaging(adminId, AdminRole.ADMIN, null, any()) } returns page

        // When & Then
        mockMvc.perform(
            get("/api/v1/vat")
                .header("X-Admin-Id", adminId.toString())
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].businessNumber").value("1234567890"))
            .andExpect(jsonPath("$.content[0].vatAmount").value(454550))
            .andExpect(jsonPath("$.content[1].businessNumber").value("0987654321"))
            .andExpect(jsonPath("$.content[1].vatAmount").value(909090))

        verify { vatCalculationService.calculateVatWithPaging(adminId, AdminRole.ADMIN, null, any()) }
    }

    @Test
    fun `특정 사업장의 부가세를 조회할 수 있다`() {
        // Given
        val adminId = 1L
        val businessNumber = "1234567890"
        val vatResult = VatCalculationService.VatResult(
            businessNumber, "테스트 주식회사", BigDecimal("10000000"), BigDecimal("5000000"), 454550L
        )
        val page = PageImpl(listOf(vatResult))

        every { vatCalculationService.calculateVatWithPaging(adminId, AdminRole.ADMIN, businessNumber, any()) } returns page

        // When & Then
        mockMvc.perform(
            get("/api/v1/vat")
                .param("businessNumber", businessNumber)
                .header("X-Admin-Id", adminId.toString())
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].businessNumber").value(businessNumber))
            .andExpect(jsonPath("$.content[0].vatAmount").value(454550))

        verify { vatCalculationService.calculateVatWithPaging(adminId, AdminRole.ADMIN, businessNumber, any()) }
    }

    @Test
    fun `MANAGER는 할당된 사업장만 조회할 수 있다`() {
        // Given
        val adminId = 2L
        val vatResults = listOf(
            VatCalculationService.VatResult("1234567890", "테스트", BigDecimal("10000000"), BigDecimal("5000000"), 454550L)
        )
        val page = PageImpl(vatResults)

        every { vatCalculationService.calculateVatWithPaging(adminId, AdminRole.MANAGER, null, any()) } returns page

        // When & Then
        mockMvc.perform(
            get("/api/v1/vat")
                .header("X-Admin-Id", adminId.toString())
                .header("X-Admin-Role", "MANAGER")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].businessNumber").value("1234567890"))
            .andExpect(jsonPath("$.content[0].vatAmount").value(454550))

        verify { vatCalculationService.calculateVatWithPaging(adminId, AdminRole.MANAGER, null, any()) }
    }

    // Edge Cases and Validation Tests

    @Test
    fun `인증 헤더 없이 부가세 조회하면 401 에러가 발생한다`() {
        // When & Then
        mockMvc.perform(
            get("/api/v1/vat")
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `AdminId 헤더만 있고 Role 헤더가 없으면 401 에러가 발생한다`() {
        // When & Then
        mockMvc.perform(
            get("/api/v1/vat")
                .header("X-Admin-Id", "1")
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `Role 헤더만 있고 AdminId 헤더가 없으면 401 에러가 발생한다`() {
        // When & Then
        mockMvc.perform(
            get("/api/v1/vat")
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `MANAGER가 권한 없는 사업장 조회 시 403 에러가 발생한다`() {
        // Given
        val adminId = 2L
        val businessNumber = "9999999999"

        every { vatCalculationService.calculateVatWithPaging(adminId, AdminRole.MANAGER, businessNumber, any()) } throws com.kcd.tax.common.exception.ForbiddenException(
            "해당 사업장에 대한 접근 권한이 없습니다"
        )

        // When & Then
        mockMvc.perform(
            get("/api/v1/vat")
                .param("businessNumber", businessNumber)
                .header("X-Admin-Id", adminId.toString())
                .header("X-Admin-Role", "MANAGER")
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `존재하지 않는 사업장 조회 시 404 에러가 발생한다`() {
        // Given
        val adminId = 1L
        val businessNumber = "9999999999"

        every { vatCalculationService.calculateVatWithPaging(adminId, AdminRole.ADMIN, businessNumber, any()) } throws com.kcd.tax.common.exception.NotFoundException(
            com.kcd.tax.common.exception.ErrorCode.BUSINESS_NOT_FOUND,
            "사업장을 찾을 수 없습니다"
        )

        // When & Then
        mockMvc.perform(
            get("/api/v1/vat")
                .param("businessNumber", businessNumber)
                .header("X-Admin-Id", adminId.toString())
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `AdminId가 숫자가 아니면 401 에러가 발생한다`() {
        // When & Then
        // Note: AdminId 파싱 실패는 인증 실패(401)로 처리됨
        mockMvc.perform(
            get("/api/v1/vat")
                .header("X-Admin-Id", "not-a-number")
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `잘못된 Role로 요청하면 401 에러가 발생한다`() {
        // When & Then
        // Note: 잘못된 Role은 인증 실패(401)로 처리됨
        mockMvc.perform(
            get("/api/v1/vat")
                .header("X-Admin-Id", "1")
                .header("X-Admin-Role", "INVALID_ROLE")
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `빈 결과를 반환할 수 있다`() {
        // Given
        val adminId = 2L
        val page = PageImpl(emptyList<VatCalculationService.VatResult>())

        every { vatCalculationService.calculateVatWithPaging(adminId, AdminRole.MANAGER, null, any()) } returns page

        // When & Then
        mockMvc.perform(
            get("/api/v1/vat")
                .header("X-Admin-Id", adminId.toString())
                .header("X-Admin-Role", "MANAGER")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content").isArray)
            .andExpect(jsonPath("$.content").isEmpty)
    }

    @Test
    fun `음수 부가세도 조회할 수 있다`() {
        // Given
        val adminId = 1L
        val businessNumber = "1234567890"
        val vatResult = VatCalculationService.VatResult(
            businessNumber, "테스트", BigDecimal("1000000"), BigDecimal("2000000"), -90910L
        )
        val page = PageImpl(listOf(vatResult))

        every { vatCalculationService.calculateVatWithPaging(adminId, AdminRole.ADMIN, businessNumber, any()) } returns page

        // When & Then
        mockMvc.perform(
            get("/api/v1/vat")
                .param("businessNumber", businessNumber)
                .header("X-Admin-Id", adminId.toString())
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].vatAmount").value(-90910))
    }

    @Test
    fun `부가세가 0인 경우도 조회할 수 있다`() {
        // Given
        val adminId = 1L
        val businessNumber = "1234567890"
        val vatResult = VatCalculationService.VatResult(
            businessNumber, "테스트", BigDecimal("1000000"), BigDecimal("1000000"), 0L
        )
        val page = PageImpl(listOf(vatResult))

        every { vatCalculationService.calculateVatWithPaging(adminId, AdminRole.ADMIN, businessNumber, any()) } returns page

        // When & Then
        mockMvc.perform(
            get("/api/v1/vat")
                .param("businessNumber", businessNumber)
                .header("X-Admin-Id", adminId.toString())
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].vatAmount").value(0))
    }

    @Test
    fun `여러 사업장의 부가세를 한번에 조회할 수 있다`() {
        // Given
        val adminId = 1L
        val vatResults = listOf(
            VatCalculationService.VatResult("1111111111", "테스트1", BigDecimal.ZERO, BigDecimal.ZERO, 0L),
            VatCalculationService.VatResult("2222222222", "테스트2", BigDecimal.ZERO, BigDecimal.ZERO, 0L),
            VatCalculationService.VatResult("3333333333", "테스트3", BigDecimal.ZERO, BigDecimal.ZERO, 0L)
        )
        val page = PageImpl(vatResults)

        every { vatCalculationService.calculateVatWithPaging(adminId, AdminRole.ADMIN, null, any()) } returns page

        // When & Then
        mockMvc.perform(
            get("/api/v1/vat")
                .header("X-Admin-Id", adminId.toString())
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(3))
            .andExpect(jsonPath("$.content[0].businessNumber").value("1111111111"))
            .andExpect(jsonPath("$.content[1].businessNumber").value("2222222222"))
            .andExpect(jsonPath("$.content[2].businessNumber").value("3333333333"))
    }

    @Test
    fun `페이지 크기를 초과하면 400 에러가 발생한다`() {
        // Given
        val largeSize = 200

        // When & Then
        mockMvc.perform(
            get("/api/v1/vat")
                .param("size", largeSize.toString())
                .header("X-Admin-Id", "1")
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isBadRequest)
    }
}
