package com.kcd.tax.common.constants

/**
 * 데이터 수집 관련 상수
 */
object CollectionConstants {
    /**
     * 수집 대기 시간 (5분)
     */
    const val COLLECTION_DELAY_MILLIS = 5 * 60 * 1000L

    /**
     * 수집 폴링 간격 (10초)
     */
    const val POLL_INTERVAL_MILLIS = 10_000L

    /**
     * Excel 파일 매출 시트명
     */
    const val SHEET_NAME_SALES = "매출"

    /**
     * Excel 파일 매입 시트명
     */
    const val SHEET_NAME_PURCHASE = "매입"

    /**
     * Excel 컬럼 인덱스: 금액
     */
    const val COLUMN_INDEX_AMOUNT = 0

    /**
     * Excel 컬럼 인덱스: 날짜
     */
    const val COLUMN_INDEX_DATE = 1

    /**
     * 매출 거래처명 prefix
     */
    const val COUNTERPARTY_PREFIX_SALES = "고객"

    /**
     * 매입 거래처명 prefix
     */
    const val COUNTERPARTY_PREFIX_PURCHASE = "공급사"
}
