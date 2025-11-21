package com.kcd.tax.common.enums

/**
 * 데이터 수집 상태
 */
enum class CollectionStatus {
    /** 수집 요청 전 */
    NOT_REQUESTED,

    /** 수집 진행 중 */
    COLLECTING,

    /** 수집 완료 */
    COLLECTED
}
