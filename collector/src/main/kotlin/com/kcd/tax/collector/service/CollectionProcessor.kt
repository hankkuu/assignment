package com.kcd.tax.collector.service

import com.kcd.tax.common.enums.CollectionStatus
import com.kcd.tax.infrastructure.domain.Transaction
import com.kcd.tax.infrastructure.helper.BusinessPlaceRepositoryHelper
import com.kcd.tax.infrastructure.repository.BusinessPlaceRepository
import com.kcd.tax.infrastructure.repository.TransactionRepository
import com.kcd.tax.collector.util.ExcelParser
import jakarta.persistence.LockModeType
import org.slf4j.LoggerFactory
import org.springframework.data.jpa.repository.Lock
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CollectionProcessor(
    private val businessPlaceHelper: BusinessPlaceRepositoryHelper,
    private val transactionRepository: TransactionRepository,
    private val businessPlaceRepository: BusinessPlaceRepository,
    private val excelParser: ExcelParser
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun start(businessNumber: String) {
        val businessPlace = businessPlaceRepository.findById(businessNumber).orElse(null)
            ?: throw IllegalStateException("BusinessPlace not found")

        businessPlace.startCollection()
        businessPlaceRepository.save(businessPlace)
        logger.info("수집 상태 변경: ${businessPlace.collectionStatus}")
    }

    @Transactional
    fun complete(businessNumber: String, transactions: List<Transaction>) {
        val businessPlace = businessPlaceRepository.findById(businessNumber).orElse(null)
            ?: throw IllegalStateException("BusinessPlace not found")

        replaceTransactionData(businessNumber, transactions)
        businessPlace.completeCollection()
        businessPlaceRepository.save(businessPlace)
        logger.info("=== 데이터 수집 완료: $businessNumber (총 ${transactions.size}건) ===")
    }

    @Transactional
    fun fail(businessNumber: String) {
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

    fun parseTransactions(dataFilePath: String, businessNumber: String): List<Transaction> {
        logger.info("엑셀 데이터 파싱 시작: $dataFilePath")
        return excelParser.parseExcelFile(dataFilePath, businessNumber)
    }

    private fun replaceTransactionData(businessNumber: String, transactions: List<Transaction>) {
        deleteExistingTransactions(businessNumber)
        saveNewTransactions(transactions)
    }

    private fun deleteExistingTransactions(businessNumber: String) {
        val deletedCount = transactionRepository.deleteByBusinessNumber(businessNumber)
        if (deletedCount > 0) {
            logger.info("기존 거래 데이터 삭제: ${deletedCount}건")
        }
    }

    private fun saveNewTransactions(transactions: List<Transaction>) {
        transactionRepository.saveAll(transactions)
        logger.info("새 거래 데이터 저장: ${transactions.size}건")
    }
}
