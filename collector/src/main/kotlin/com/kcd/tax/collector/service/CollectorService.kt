package com.kcd.tax.collector.service

import com.kcd.tax.common.constants.CollectionConstants
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

/**
 * 데이터 수집기 서비스 (비동기 처리)
 *
 * 실제 데이터 수집을 비동기로 처리
 * - 상태를 COLLECTING으로 변경
 * - 5분 대기 (실제 수집 시뮬레이션)
 * - 엑셀 데이터 파싱 및 DB 적재
 * - 상태를 COLLECTED로 변경
 *
 * 리팩토링: 긴 메서드를 작은 메서드로 분해하여 가독성과 테스트 가능성 향상
 */
@Service
class CollectorService(
    private val collectionProcessor: CollectionProcessor,
    @param:Value("\${collector.data-file}") private val dataFilePath: String
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 데이터 수집 비동기 실행
     *
     * @param businessNumber 사업자번호
     */
    @Async
    fun collectData(businessNumber: String) {
        logger.info("=== 데이터 수집 시작: $businessNumber ===")

        try {
            collectionProcessor.start(businessNumber)

            waitForCollection()
            val transactions = collectionProcessor.parseTransactions(dataFilePath, businessNumber)

            collectionProcessor.complete(businessNumber, transactions)
        } catch (e: Exception) {
            logger.error("=== 데이터 수집 실패: $businessNumber ===", e)
            collectionProcessor.fail(businessNumber)
            throw e
        }
    }

    /**
     * 수집 대기 (5분 시뮬레이션)
     */
    private fun waitForCollection() {
        logger.info("데이터 수집 중... (5분 대기)")
        Thread.sleep(CollectionConstants.COLLECTION_DELAY_MILLIS)
    }
}

