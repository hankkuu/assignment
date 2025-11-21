package com.kcd.tax.api.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.kcd.tax.api.controller.dto.request.CollectionRequest
import com.kcd.tax.api.service.CollectionService
import com.kcd.tax.common.enums.CollectionStatus
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    classes = [com.kcd.tax.api.TaxApiApplication::class]
)
@AutoConfigureMockMvc
class CollectionControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var collectionService: CollectionService

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Test
    fun `수집을 요청할 수 있다`() {
        // Given
        val businessNumber = "1234567890"
        val request = CollectionRequest(businessNumber)

        every { collectionService.requestCollection(businessNumber) } returns CollectionStatus.NOT_REQUESTED

        // When & Then
        mockMvc.perform(
            post("/api/v1/collections")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("X-Admin-Id", "1")
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("NOT_REQUESTED"))

        verify { collectionService.requestCollection(businessNumber) }
    }

    @Test
    fun `수집 상태를 조회할 수 있다`() {
        // Given
        val businessNumber = "1234567890"

        every { collectionService.getCollectionStatus(businessNumber) } returns CollectionStatus.COLLECTING

        // When & Then
        mockMvc.perform(
            get("/api/v1/collections/$businessNumber/status")
                .header("X-Admin-Id", "1")
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.businessNumber").value(businessNumber))
            .andExpect(jsonPath("$.status").value("COLLECTING"))

        verify { collectionService.getCollectionStatus(businessNumber) }
    }

    @Test
    fun `잘못된 요청 시 400 에러가 발생한다`() {
        // Given
        val invalidRequest = """{"businessNumber": ""}"""

        // When & Then
        mockMvc.perform(
            post("/api/v1/collections")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequest)
                .header("X-Admin-Id", "1")
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isBadRequest)
    }

    // Edge Cases and Validation Tests

    @Test
    fun `사업자번호가 10자리가 아니면 400 에러가 발생한다`() {
        // Given
        val invalidRequest = """{"businessNumber": "12345"}"""

        // When & Then
        mockMvc.perform(
            post("/api/v1/collections")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequest)
                .header("X-Admin-Id", "1")
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `사업자번호에 숫자가 아닌 문자가 포함되면 400 에러가 발생한다`() {
        // Given
        val invalidRequest = """{"businessNumber": "123456789A"}"""

        // When & Then
        mockMvc.perform(
            post("/api/v1/collections")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequest)
                .header("X-Admin-Id", "1")
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `존재하지 않는 사업장에 수집 요청하면 404 에러가 발생한다`() {
        // Given
        val businessNumber = "9999999999"
        val request = CollectionRequest(businessNumber)

        every { collectionService.requestCollection(businessNumber) } throws com.kcd.tax.common.exception.NotFoundException(
            com.kcd.tax.common.exception.ErrorCode.BUSINESS_NOT_FOUND,
            "사업장을 찾을 수 없습니다"
        )

        // When & Then
        mockMvc.perform(
            post("/api/v1/collections")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("X-Admin-Id", "1")
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `이미 수집 중인 사업장에 수집 요청하면 409 에러가 발생한다`() {
        // Given
        val businessNumber = "1234567890"
        val request = CollectionRequest(businessNumber)

        every { collectionService.requestCollection(businessNumber) } throws com.kcd.tax.common.exception.ConflictException(
            com.kcd.tax.common.exception.ErrorCode.COLLECTION_ALREADY_IN_PROGRESS,
            "이미 수집 중입니다"
        )

        // When & Then
        mockMvc.perform(
            post("/api/v1/collections")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("X-Admin-Id", "1")
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isConflict)
    }

    @Test
    fun `인증 헤더 없이 수집 요청하면 401 에러가 발생한다`() {
        // Given
        val request = CollectionRequest("1234567890")

        // When & Then
        mockMvc.perform(
            post("/api/v1/collections")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `인증 헤더 없이 상태 조회하면 401 에러가 발생한다`() {
        // When & Then
        mockMvc.perform(
            get("/api/v1/collections/1234567890/status")
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `존재하지 않는 사업장의 상태 조회하면 404 에러가 발생한다`() {
        // Given
        val businessNumber = "9999999999"

        every { collectionService.getCollectionStatus(businessNumber) } throws com.kcd.tax.common.exception.NotFoundException(
            com.kcd.tax.common.exception.ErrorCode.BUSINESS_NOT_FOUND,
            "사업장을 찾을 수 없습니다"
        )

        // When & Then
        mockMvc.perform(
            get("/api/v1/collections/$businessNumber/status")
                .header("X-Admin-Id", "1")
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `잘못된 JSON 형식으로 요청하면 500 에러가 발생한다`() {
        // Given
        val invalidJson = """{"businessNumber": """

        // When & Then
        // Note: 잘못된 JSON은 Spring에서 500 Internal Server Error로 처리됨
        mockMvc.perform(
            post("/api/v1/collections")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson)
                .header("X-Admin-Id", "1")
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isInternalServerError)
    }

    @Test
    fun `NULL 사업자번호로 요청하면 500 에러가 발생한다`() {
        // Given
        val invalidRequest = """{"businessNumber": null}"""

        // When & Then
        // Note: NULL 값은 Jackson 역직렬화 실패로 500 Internal Server Error 발생
        mockMvc.perform(
            post("/api/v1/collections")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequest)
                .header("X-Admin-Id", "1")
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isInternalServerError)
    }

    @Test
    fun `MANAGER도 수집을 요청할 수 있다`() {
        // Given
        val businessNumber = "1234567890"
        val request = CollectionRequest(businessNumber)

        every { collectionService.requestCollection(businessNumber) } returns CollectionStatus.NOT_REQUESTED

        // When & Then
        mockMvc.perform(
            post("/api/v1/collections")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("X-Admin-Id", "2")
                .header("X-Admin-Role", "MANAGER")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("NOT_REQUESTED"))

        verify { collectionService.requestCollection(businessNumber) }
    }

    @Test
    fun `MANAGER도 상태를 조회할 수 있다`() {
        // Given
        val businessNumber = "1234567890"

        every { collectionService.getCollectionStatus(businessNumber) } returns CollectionStatus.COLLECTING

        // When & Then
        mockMvc.perform(
            get("/api/v1/collections/$businessNumber/status")
                .header("X-Admin-Id", "2")
                .header("X-Admin-Role", "MANAGER")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("COLLECTING"))

        verify { collectionService.getCollectionStatus(businessNumber) }
    }

    @Test
    fun `AdminId가 숫자가 아니면 401 에러가 발생한다`() {
        // Given
        val request = CollectionRequest("1234567890")

        // When & Then
        // Note: AdminId 파싱 실패는 인증 실패(401)로 처리됨
        mockMvc.perform(
            post("/api/v1/collections")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("X-Admin-Id", "not-a-number")
                .header("X-Admin-Role", "ADMIN")
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `잘못된 Role로 요청하면 401 에러가 발생한다`() {
        // Given
        val request = CollectionRequest("1234567890")

        // When & Then
        // Note: 잘못된 Role은 인증 실패(401)로 처리됨
        mockMvc.perform(
            post("/api/v1/collections")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("X-Admin-Id", "1")
                .header("X-Admin-Role", "INVALID_ROLE")
        )
            .andExpect(status().isUnauthorized)
    }
}
