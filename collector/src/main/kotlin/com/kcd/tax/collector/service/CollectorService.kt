package com.kcd.tax.collector.service

import com.kcd.tax.common.constants.CollectionConstants
import com.kcd.tax.common.enums.CollectionStatus
import com.kcd.tax.infrastructure.domain.BusinessPlace
import com.kcd.tax.infrastructure.domain.Transaction
import com.kcd.tax.infrastructure.helper.BusinessPlaceRepositoryHelper
import com.kcd.tax.infrastructure.repository.TransactionRepository
import com.kcd.tax.infrastructure.util.ExcelParser
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

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
    private val businessPlaceHelper: BusinessPlaceRepositoryHelper,
    private val transactionRepository: TransactionRepository,
    private val excelParser: ExcelParser,
    @param:Value("\${collector.data-file}") private val dataFilePath: String
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 데이터 수집 비동기 실행
     *
     * @param businessNumber 사업자번호
     */
    @Async
    @Transactional
    fun collectData(businessNumber: String) {
        logger.info("=== 데이터 수집 시작: $businessNumber ===")

        try {
            val businessPlace = businessPlaceHelper.findByIdOrThrow(businessNumber)

            startCollectionProcess(businessPlace)
            waitForCollection()
            val transactions = parseTransactions(businessNumber)
            replaceTransactionData(businessNumber, transactions)
            completeCollectionProcess(businessPlace, transactions.size)

        } catch (e: Exception) {
            logger.error("=== 데이터 수집 실패: $businessNumber ===", e)
            handleCollectionFailure(businessNumber)
            throw e
        }
    }

    /**
     * 수집 프로세스 시작 (상태 변경)
     */
    private fun startCollectionProcess(businessPlace: BusinessPlace) {
        businessPlace.startCollection()
        businessPlaceHelper.save(businessPlace)
        logger.info("수집 상태 변경: ${businessPlace.collectionStatus}")
    }

    /**
     * 수집 대기 (5분 시뮬레이션)
     */
    private fun waitForCollection() {
        logger.info("데이터 수집 중... (5분 대기)")
        Thread.sleep(CollectionConstants.COLLECTION_DELAY_MILLIS)
    }

    /**
     * Excel 파일에서 거래 데이터 파싱
     */
    private fun parseTransactions(businessNumber: String): List<Transaction> {
        logger.info("엑셀 데이터 파싱 시작: $dataFilePath")
        return excelParser.parseExcelFile(dataFilePath, businessNumber)
    }

    /**
     * 기존 데이터 삭제 후 새 데이터 저장
     */
    private fun replaceTransactionData(businessNumber: String, transactions: List<Transaction>) {
        deleteExistingTransactions(businessNumber)
        saveNewTransactions(transactions)
    }

    /**
     * 기존 거래 데이터 삭제
     */
    private fun deleteExistingTransactions(businessNumber: String) {
        val deletedCount = transactionRepository.deleteByBusinessNumber(businessNumber)
        if (deletedCount > 0) {
            logger.info("기존 거래 데이터 삭제: ${deletedCount}건")
        }
    }

    /**
     * 새 거래 데이터 저장
     */
    private fun saveNewTransactions(transactions: List<Transaction>) {
        transactionRepository.saveAll(transactions)
        logger.info("새 거래 데이터 저장: ${transactions.size}건")
    }

    /**
     * 수집 프로세스 완료 (상태 변경)
     */
    private fun completeCollectionProcess(businessPlace: BusinessPlace, transactionCount: Int) {
        businessPlace.completeCollection()
        businessPlaceHelper.save(businessPlace)
        logger.info("=== 데이터 수집 완료: ${businessPlace.businessNumber} (총 ${transactionCount}건) ===")
    }

    /**
     * 수집 실패 시 상태 복원
     */
    private fun handleCollectionFailure(businessNumber: String) {
        try {
            val businessPlace = businessPlaceHelper.findByIdOrThrow(businessNumber)
            if (businessPlace.collectionStatus == CollectionStatus.COLLECTING) {
                businessPlace.resetCollection()
                businessPlaceHelper.save(businessPlace)
                logger.info("수집 상태 복원: NOT_REQUESTED")
            }
        } catch (resetException: Exception) {
            logger.error("상태 복원 실패", resetException)
        }
    }
}
