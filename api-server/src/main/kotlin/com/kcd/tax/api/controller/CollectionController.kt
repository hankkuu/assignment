package com.kcd.tax.api.controller

import com.kcd.tax.api.controller.dto.request.CollectionRequest
import com.kcd.tax.api.controller.dto.response.CollectionStatusResponse
import com.kcd.tax.api.security.annotation.RequireAuth
import com.kcd.tax.api.service.CollectionService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * 데이터 수집 API Controller
 *
 * - POST /api/v1/collections: 수집 요청
 * - GET /api/v1/collections/{businessNumber}/status: 수집 상태 조회
 *
 * 로깅은 ControllerLoggingAspect에서 AOP로 자동 처리
 */
@RestController
@RequestMapping("/api/v1/collections")
@RequireAuth
class CollectionController(
    private val collectionService: CollectionService
) {
    /**
     * 수집 요청
     *
     * @param request 수집 요청 (사업자번호)
     * @return 수집 상태 응답
     */
    @PostMapping
    fun requestCollection(
        @Valid @RequestBody request: CollectionRequest
    ): ResponseEntity<CollectionStatusResponse> {
        val status = collectionService.requestCollection(request.businessNumber)

        val response = CollectionStatusResponse.of(
            businessNumber = request.businessNumber,
            status = status,
            message = "수집 요청이 접수되었습니다. Collector가 처리 예정입니다."
        )

        return ResponseEntity.ok(response)
    }

    /**
     * 수집 상태 조회
     *
     * @param businessNumber 사업자번호
     * @return 수집 상태 응답
     */
    @GetMapping("/{businessNumber}/status")
    fun getCollectionStatus(
        @PathVariable businessNumber: String
    ): ResponseEntity<CollectionStatusResponse> {
        val status = collectionService.getCollectionStatus(businessNumber)

        val response = CollectionStatusResponse.of(
            businessNumber = businessNumber,
            status = status
        )

        return ResponseEntity.ok(response)
    }
}
