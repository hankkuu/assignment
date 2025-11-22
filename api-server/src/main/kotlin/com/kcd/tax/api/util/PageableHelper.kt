package com.kcd.tax.api.util

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable

/**
 * 페이징 유틸리티 헬퍼
 *
 * 메모리 상의 컬렉션을 페이징 처리하는 재사용 가능한 유틸리티
 * 주로 복잡한 권한 필터링 후 페이징이 필요한 경우 사용
 *
 * ## 사용 예시
 * ```kotlin
 * val items = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
 * val pageable = PageRequest.of(0, 3)
 * val page: Page<Int> = PageableHelper.createPage(items, pageable)
 * ```
 *
 * ## 주의사항
 * - 이 방식은 메모리 기반 페이징이므로 대용량 데이터에는 적합하지 않음
 * - 가능하면 Repository 레이어에서 DB 페이징(LIMIT/OFFSET)을 사용하는 것이 권장됨
 * - 권한 필터링 등 비즈니스 로직 후 페이징이 필요한 경우에만 사용
 */
object PageableHelper {

    /**
     * 컬렉션을 페이징 처리하여 Page 객체 반환
     *
     * @param items 페이징할 전체 아이템 리스트
     * @param pageable 페이징 정보
     * @return Page 객체 (페이징된 아이템 + 메타데이터)
     */
    fun <T> createPage(items: List<T>, pageable: Pageable): Page<T> {
        val totalElements = items.size
        val pagedItems = extractPagedItems(items, pageable)

        return PageImpl(pagedItems, pageable, totalElements.toLong())
    }

    /**
     * 컬렉션에서 요청된 페이지의 아이템만 추출
     *
     * @param items 전체 아이템 리스트
     * @param pageable 페이징 정보
     * @return 요청된 페이지의 아이템 리스트
     */
    fun <T> extractPagedItems(items: List<T>, pageable: Pageable): List<T> {
        val totalElements = items.size

        // 시작 인덱스 계산 (범위 초과 시 전체 크기로 제한)
        val start = (pageable.pageNumber * pageable.pageSize).coerceAtMost(totalElements)

        // 종료 인덱스 계산 (범위 초과 시 전체 크기로 제한)
        val end = (start + pageable.pageSize).coerceAtMost(totalElements)

        // 시작 인덱스가 전체 크기를 초과하면 빈 리스트 반환
        return if (start < totalElements) {
            items.subList(start, end)
        } else {
            emptyList()
        }
    }

    /**
     * 페이징 가능 여부 확인
     *
     * @param items 아이템 리스트
     * @param pageable 페이징 정보
     * @return 다음 페이지가 존재하는지 여부
     */
    fun <T> hasNext(items: List<T>, pageable: Pageable): Boolean {
        val totalElements = items.size
        val nextPageStart = (pageable.pageNumber + 1) * pageable.pageSize
        return nextPageStart < totalElements
    }

    /**
     * 전체 페이지 수 계산
     *
     * @param totalElements 전체 아이템 개수
     * @param pageSize 페이지 크기
     * @return 전체 페이지 수
     */
    fun calculateTotalPages(totalElements: Int, pageSize: Int): Int {
        if (pageSize <= 0) return 0
        return (totalElements + pageSize - 1) / pageSize  // 올림 계산
    }
}
