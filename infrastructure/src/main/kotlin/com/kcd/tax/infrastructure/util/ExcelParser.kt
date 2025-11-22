package com.kcd.tax.infrastructure.util

import com.kcd.tax.common.constants.CollectionConstants
import com.kcd.tax.common.exception.BadRequestException
import com.kcd.tax.common.exception.ErrorCode
import com.kcd.tax.infrastructure.domain.Transaction
import com.kcd.tax.common.enums.TransactionType
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.File
import java.math.BigDecimal
import java.nio.file.Paths
import java.time.LocalDate
import kotlin.random.Random

/**
 * 엑셀 파일 파싱 유틸리티
 *
 * 매출/매입 데이터를 엑셀 파일에서 읽어 Transaction 엔티티로 변환
 */
@Component
class ExcelParser {

    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val SAMPLE_SALES_COUNT = 10
        private const val SAMPLE_PURCHASE_COUNT = 8
    }

    /**
     * 샘플 거래 데이터 생성
     * 실제 환경에서는 parseExcelFile() 메서드를 사용
     *
     * @param businessNumber 사업자번호
     * @return 생성된 거래 내역 리스트
     */
    fun parseTransactions(businessNumber: String): List<Transaction> {
        logger.info("샘플 거래 데이터 생성 시작: {}", businessNumber)
        return generateSampleData(businessNumber)
    }

    /**
     * 샘플 데이터 생성 (시뮬레이션용)
     *
     * 매출 10건, 매입 8건 생성
     */
    private fun generateSampleData(businessNumber: String): List<Transaction> {
        val transactions = mutableListOf<Transaction>()
        val random = Random.Default

        // 매출 데이터 생성
        repeat(SAMPLE_SALES_COUNT) { index ->
            transactions.add(
                Transaction(
                    businessNumber = businessNumber,
                    type = TransactionType.SALES,
                    amount = BigDecimal(random.nextInt(1_000_000, 5_000_000)),
                    counterpartyName = "${CollectionConstants.COUNTERPARTY_PREFIX_SALES}${index + 1}",
                    transactionDate = LocalDate.now().minusDays(index.toLong())
                )
            )
        }

        // 매입 데이터 생성
        repeat(SAMPLE_PURCHASE_COUNT) { index ->
            transactions.add(
                Transaction(
                    businessNumber = businessNumber,
                    type = TransactionType.PURCHASE,
                    amount = BigDecimal(random.nextInt(500_000, 2_000_000)),
                    counterpartyName = "${CollectionConstants.COUNTERPARTY_PREFIX_PURCHASE}${index + 1}",
                    transactionDate = LocalDate.now().minusDays(index.toLong())
                )
            )
        }

        logger.info("샘플 데이터 생성 완료: 총 {}건 (매출 {}건, 매입 {}건)", transactions.size, SAMPLE_SALES_COUNT, SAMPLE_PURCHASE_COUNT)
        return transactions
    }

    /**
     * 실제 엑셀 파일 파싱
     *
     * @param filePath 엑셀 파일 경로
     * @param businessNumber 사업자번호
     * @return 파싱된 거래 내역 리스트
     * @throws InvalidInputException 파일 경로가 유효하지 않은 경우
     *
     * 엑셀 파일 구조:
     * - 시트: "매출", "매입"
     * - 컬럼: 금액 | 날짜 (2개 컬럼)
     * - 헤더 없음 (첫 번째 행부터 데이터)
     * - 거래처명은 자동 생성됨 (고객1, 공급사1 등)
     */
    fun parseExcelFile(filePath: String, businessNumber: String): List<Transaction> {
        logger.info("엑셀 파일 파싱 시작: {}", filePath)

        // Path Traversal 공격 방지
        validateFilePath(filePath)

        val transactions = mutableListOf<Transaction>()
        val file = File(filePath)

        if (!file.exists()) {
            logger.warn("엑셀 파일을 찾을 수 없습니다: {}", filePath)
            return emptyList()
        }

        WorkbookFactory.create(file).use { workbook ->
            // 매출 시트 파싱
            workbook.getSheet(CollectionConstants.SHEET_NAME_SALES)?.let { sheet ->
                logger.info("${CollectionConstants.SHEET_NAME_SALES} 시트 파싱 중...")
                transactions.addAll(parseSheet(sheet, businessNumber, TransactionType.SALES))
            }

            // 매입 시트 파싱
            workbook.getSheet(CollectionConstants.SHEET_NAME_PURCHASE)?.let { sheet ->
                logger.info("${CollectionConstants.SHEET_NAME_PURCHASE} 시트 파싱 중...")
                transactions.addAll(parseSheet(sheet, businessNumber, TransactionType.PURCHASE))
            }
        }

        logger.info("엑셀 파일 파싱 완료: 총 ${transactions.size}건")
        return transactions
    }

    /**
     * 엑셀 시트 파싱
     *
     * @param sheet 파싱할 시트
     * @param businessNumber 사업자번호
     * @param type 거래 타입
     * @return 파싱된 거래 내역 리스트
     *
     * 파일 형식: [금액, 날짜] (헤더 없음, 2개 컬럼)
     */
    private fun parseSheet(
        sheet: Sheet,
        businessNumber: String,
        type: TransactionType
    ): List<Transaction> {
        val transactions = mutableListOf<Transaction>()
        val counterpartyPrefix = if (type == TransactionType.SALES) {
            CollectionConstants.COUNTERPARTY_PREFIX_SALES
        } else {
            CollectionConstants.COUNTERPARTY_PREFIX_PURCHASE
        }

        // 헤더 없이 첫 행부터 데이터 (index 0부터 시작)
        for (rowIndex in 0 until sheet.physicalNumberOfRows) {
            val row = sheet.getRow(rowIndex) ?: continue

            try {
                // 컬럼 형식: [금액(0), 날짜(1)]
                val amount = row.getCell(CollectionConstants.COLUMN_INDEX_AMOUNT)?.numericCellValue?.let { BigDecimal(it) }
                    ?: continue  // 금액이 없으면 스킵

                val date = row.getCell(CollectionConstants.COLUMN_INDEX_DATE)?.let { cell ->
                    when {
                        cell.cellType == org.apache.poi.ss.usermodel.CellType.NUMERIC &&
                        org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell) ->
                            cell.localDateTimeCellValue?.toLocalDate()
                        else -> LocalDate.now()
                    }
                } ?: LocalDate.now()

                // 거래처명 자동 생성 (고객1, 고객2... or 공급사1, 공급사2...)
                val counterparty = "$counterpartyPrefix${rowIndex + 1}"

                transactions.add(
                    Transaction(
                        businessNumber = businessNumber,
                        type = type,
                        amount = amount,
                        vatAmount = null,  // 부가세 별도 없음
                        counterpartyName = counterparty,
                        transactionDate = date
                    )
                )
            } catch (e: Exception) {
                logger.warn("행 파싱 실패 (행: ${rowIndex + 1}): ${e.message}")
                // 실패한 행은 스킵하고 계속 진행
            }
        }

        logger.info("${type.name} 시트 파싱 완료: ${transactions.size}건")
        return transactions
    }

    /**
     * 파일 경로 유효성 검증 (Path Traversal 공격 방지)
     *
     * @param filePath 검증할 파일 경로
     * @throws BadRequestException 경로에 위험한 문자가 포함된 경우
     */
    private fun validateFilePath(filePath: String) {
        // Null 또는 빈 문자열 체크
        if (filePath.isBlank()) {
            throw BadRequestException(ErrorCode.INVALID_INPUT, "파일 경로가 비어있습니다")
        }

        // 경로 순회 패턴 체크 ("..", "./", ".\\")
        val dangerousPatterns = listOf("..", "./", ".\\")
        if (dangerousPatterns.any { filePath.contains(it) }) {
            logger.warn("경로 순회 공격 시도 감지: {}", filePath)
            throw BadRequestException(ErrorCode.INVALID_INPUT, "유효하지 않은 파일 경로입니다")
        }

        // 절대 경로로 변환하여 정규화
        val file = File(filePath)
        val canonicalPath = try {
            file.canonicalPath
        } catch (e: Exception) {
            logger.warn("파일 경로 정규화 실패: {}", filePath, e)
            throw BadRequestException(ErrorCode.INVALID_INPUT, "유효하지 않은 파일 경로입니다")
        }

        // 파일 확장자 체크 (.xlsx, .xls만 허용)
        val allowedExtensions = listOf(".xlsx", ".xls")
        if (!allowedExtensions.any { canonicalPath.lowercase().endsWith(it) }) {
            logger.warn("허용되지 않은 파일 확장자: {}", canonicalPath)
            throw BadRequestException(ErrorCode.INVALID_INPUT, "엑셀 파일만 허용됩니다 (.xlsx, .xls)")
        }

        logger.debug("파일 경로 검증 통과: {}", canonicalPath)
    }
}
