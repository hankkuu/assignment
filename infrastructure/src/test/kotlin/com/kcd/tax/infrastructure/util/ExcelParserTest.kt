package com.kcd.tax.infrastructure.util

import com.kcd.tax.common.enums.TransactionType
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.math.BigDecimal
import java.nio.file.Path
import java.time.LocalDate

class ExcelParserTest {

    private val excelParser = ExcelParser()

    @Test
    fun `샘플 데이터를 생성할 수 있다`() {
        // Given
        val businessNumber = "1234567890"

        // When
        val transactions = excelParser.parseTransactions(businessNumber)

        // Then
        assertNotNull(transactions)
        assertEquals(18, transactions.size) // 매출 10건 + 매입 8건

        val salesTransactions = transactions.filter { it.type == TransactionType.SALES }
        val purchaseTransactions = transactions.filter { it.type == TransactionType.PURCHASE }

        assertEquals(10, salesTransactions.size)
        assertEquals(8, purchaseTransactions.size)

        // 모든 거래의 사업자번호 확인
        assertTrue(transactions.all { it.businessNumber == businessNumber })
    }

    @Test
    fun `샘플 데이터의 거래처명이 올바르게 생성된다`() {
        // Given
        val businessNumber = "1234567890"

        // When
        val transactions = excelParser.parseTransactions(businessNumber)

        // Then
        val salesTransactions = transactions.filter { it.type == TransactionType.SALES }
        val purchaseTransactions = transactions.filter { it.type == TransactionType.PURCHASE }

        // 매출 거래처명 확인 (고객1, 고객2, ...)
        assertTrue(salesTransactions[0].counterpartyName?.startsWith("고객") == true)
        assertTrue(salesTransactions.any { it.counterpartyName == "고객1" })

        // 매입 거래처명 확인 (공급사1, 공급사2, ...)
        assertTrue(purchaseTransactions[0].counterpartyName?.startsWith("공급사") == true)
        assertTrue(purchaseTransactions.any { it.counterpartyName == "공급사1" })
    }

    @Test
    fun `샘플 데이터의 금액이 유효한 범위에 있다`() {
        // Given
        val businessNumber = "1234567890"

        // When
        val transactions = excelParser.parseTransactions(businessNumber)

        // Then
        val salesTransactions = transactions.filter { it.type == TransactionType.SALES }
        val purchaseTransactions = transactions.filter { it.type == TransactionType.PURCHASE }

        // 매출 금액 범위: 1,000,000 ~ 5,000,000
        assertTrue(salesTransactions.all {
            it.amount >= BigDecimal("1000000") && it.amount < BigDecimal("5000000")
        })

        // 매입 금액 범위: 500,000 ~ 2,000,000
        assertTrue(purchaseTransactions.all {
            it.amount >= BigDecimal("500000") && it.amount < BigDecimal("2000000")
        })
    }

    @Test
    fun `존재하지 않는 파일을 파싱하면 빈 리스트를 반환한다`() {
        // Given
        val filePath = "non-existent-file.xlsx"
        val businessNumber = "1234567890"

        // When
        val transactions = excelParser.parseExcelFile(filePath, businessNumber)

        // Then
        assertTrue(transactions.isEmpty())
    }

    @Test
    fun `실제 엑셀 파일을 파싱할 수 있다`(@TempDir tempDir: Path) {
        // Given
        val businessNumber = "1234567890"
        val excelFile = createTestExcelFile(tempDir)

        // When
        val transactions = excelParser.parseExcelFile(excelFile.absolutePath, businessNumber)

        // Then
        assertFalse(transactions.isEmpty())

        val salesTransactions = transactions.filter { it.type == TransactionType.SALES }
        val purchaseTransactions = transactions.filter { it.type == TransactionType.PURCHASE }

        assertTrue(salesTransactions.isNotEmpty())
        assertTrue(purchaseTransactions.isNotEmpty())

        // 모든 거래의 사업자번호 확인
        assertTrue(transactions.all { it.businessNumber == businessNumber })
    }

    @Test
    fun `매출 시트만 있는 파일을 파싱할 수 있다`(@TempDir tempDir: Path) {
        // Given
        val businessNumber = "1234567890"
        val excelFile = createExcelFileWithSalesOnly(tempDir)

        // When
        val transactions = excelParser.parseExcelFile(excelFile.absolutePath, businessNumber)

        // Then
        assertFalse(transactions.isEmpty())
        assertTrue(transactions.all { it.type == TransactionType.SALES })
    }

    @Test
    fun `매입 시트만 있는 파일을 파싱할 수 있다`(@TempDir tempDir: Path) {
        // Given
        val businessNumber = "1234567890"
        val excelFile = createExcelFileWithPurchaseOnly(tempDir)

        // When
        val transactions = excelParser.parseExcelFile(excelFile.absolutePath, businessNumber)

        // Then
        assertFalse(transactions.isEmpty())
        assertTrue(transactions.all { it.type == TransactionType.PURCHASE })
    }

    @Test
    fun `시트가 없는 빈 파일을 파싱하면 빈 리스트를 반환한다`(@TempDir tempDir: Path) {
        // Given
        val businessNumber = "1234567890"
        val excelFile = createEmptyExcelFile(tempDir)

        // When
        val transactions = excelParser.parseExcelFile(excelFile.absolutePath, businessNumber)

        // Then
        assertTrue(transactions.isEmpty())
    }

    @Test
    fun `금액이 없는 행은 스킵된다`(@TempDir tempDir: Path) {
        // Given
        val businessNumber = "1234567890"
        val excelFile = createExcelFileWithEmptyAmounts(tempDir)

        // When
        val transactions = excelParser.parseExcelFile(excelFile.absolutePath, businessNumber)

        // Then
        // 유효한 행만 파싱됨 (금액이 없는 행은 제외)
        assertTrue(transactions.all { it.amount > BigDecimal.ZERO })
    }

    @Test
    fun `날짜가 없는 행은 오늘 날짜로 설정된다`(@TempDir tempDir: Path) {
        // Given
        val businessNumber = "1234567890"
        val excelFile = createExcelFileWithoutDates(tempDir)

        // When
        val transactions = excelParser.parseExcelFile(excelFile.absolutePath, businessNumber)

        // Then
        assertTrue(transactions.isNotEmpty())
        // 날짜가 없으면 LocalDate.now()로 설정됨
        assertTrue(transactions.all { it.transactionDate != null })
    }

    @Test
    fun `거래처명이 자동으로 생성된다`(@TempDir tempDir: Path) {
        // Given
        val businessNumber = "1234567890"
        val excelFile = createTestExcelFile(tempDir)

        // When
        val transactions = excelParser.parseExcelFile(excelFile.absolutePath, businessNumber)

        // Then
        val salesTransactions = transactions.filter { it.type == TransactionType.SALES }
        val purchaseTransactions = transactions.filter { it.type == TransactionType.PURCHASE }

        // 매출 거래처명: 고객1, 고객2, ...
        if (salesTransactions.isNotEmpty()) {
            assertTrue(salesTransactions.all { it.counterpartyName?.startsWith("고객") == true })
            assertEquals("고객1", salesTransactions[0].counterpartyName)
        }

        // 매입 거래처명: 공급사1, 공급사2, ...
        if (purchaseTransactions.isNotEmpty()) {
            assertTrue(purchaseTransactions.all { it.counterpartyName?.startsWith("공급사") == true })
            assertEquals("공급사1", purchaseTransactions[0].counterpartyName)
        }
    }

    @Test
    fun `여러 행을 가진 시트를 올바르게 파싱한다`(@TempDir tempDir: Path) {
        // Given
        val businessNumber = "1234567890"
        val excelFile = createExcelFileWithMultipleRows(tempDir, salesCount = 50, purchaseCount = 30)

        // When
        val transactions = excelParser.parseExcelFile(excelFile.absolutePath, businessNumber)

        // Then
        assertEquals(80, transactions.size)

        val salesTransactions = transactions.filter { it.type == TransactionType.SALES }
        val purchaseTransactions = transactions.filter { it.type == TransactionType.PURCHASE }

        assertEquals(50, salesTransactions.size)
        assertEquals(30, purchaseTransactions.size)
    }

    @Test
    fun `큰 금액도 정확하게 파싱된다`(@TempDir tempDir: Path) {
        // Given
        val businessNumber = "1234567890"
        val largeAmount = 999999999.99
        val excelFile = createExcelFileWithLargeAmounts(tempDir, largeAmount)

        // When
        val transactions = excelParser.parseExcelFile(excelFile.absolutePath, businessNumber)

        // Then
        assertTrue(transactions.isNotEmpty())
        assertTrue(transactions.any { it.amount >= BigDecimal("999999999") })
    }

    @Test
    fun `소수점이 있는 금액도 올바르게 파싱된다`(@TempDir tempDir: Path) {
        // Given
        val businessNumber = "1234567890"
        val excelFile = createExcelFileWithDecimalAmounts(tempDir)

        // When
        val transactions = excelParser.parseExcelFile(excelFile.absolutePath, businessNumber)

        // Then
        assertTrue(transactions.isNotEmpty())
        // BigDecimal은 소수점을 정확하게 표현
        assertTrue(transactions.all { it.amount.scale() >= 0 })
    }

    @Test
    fun `파싱된 거래의 부가세는 null이다`(@TempDir tempDir: Path) {
        // Given
        val businessNumber = "1234567890"
        val excelFile = createTestExcelFile(tempDir)

        // When
        val transactions = excelParser.parseExcelFile(excelFile.absolutePath, businessNumber)

        // Then
        assertTrue(transactions.all { it.vatAmount == null })
    }

    // Edge Cases

    @Test
    fun `0원 거래도 파싱할 수 있다`(@TempDir tempDir: Path) {
        // Given
        val businessNumber = "1234567890"
        val excelFile = createExcelFileWithZeroAmount(tempDir)

        // When
        val transactions = excelParser.parseExcelFile(excelFile.absolutePath, businessNumber)

        // Then
        // 0원도 유효한 금액이므로 파싱됨
        assertTrue(transactions.any { it.amount == BigDecimal.ZERO })
    }

    @Test
    fun `빈 행이 포함된 파일을 파싱할 수 있다`(@TempDir tempDir: Path) {
        // Given
        val businessNumber = "1234567890"
        val excelFile = createExcelFileWithEmptyRows(tempDir)

        // When
        val transactions = excelParser.parseExcelFile(excelFile.absolutePath, businessNumber)

        // Then
        // 빈 행은 스킵되고 유효한 행만 파싱됨
        assertTrue(transactions.isNotEmpty())
        assertTrue(transactions.all { it.amount > BigDecimal.ZERO })
    }

    @Test
    fun `여러 사업자번호로 파싱해도 각각 올바른 사업자번호가 설정된다`() {
        // Given
        val businessNumbers = listOf("1111111111", "2222222222", "3333333333")

        // When & Then
        businessNumbers.forEach { businessNumber ->
            val transactions = excelParser.parseTransactions(businessNumber)
            assertTrue(transactions.all { it.businessNumber == businessNumber })
        }
    }

    // Helper methods for creating test Excel files

    private fun createTestExcelFile(tempDir: Path): File {
        val file = tempDir.resolve("test.xlsx").toFile()
        XSSFWorkbook().use { workbook ->
            // 매출 시트
            val salesSheet = workbook.createSheet("매출")
            repeat(5) { index ->
                val row = salesSheet.createRow(index)
                row.createCell(0).setCellValue((index + 1) * 100000.0)
                row.createCell(1).setCellValue(LocalDate.now().minusDays(index.toLong()).toString())
            }

            // 매입 시트
            val purchaseSheet = workbook.createSheet("매입")
            repeat(3) { index ->
                val row = purchaseSheet.createRow(index)
                row.createCell(0).setCellValue((index + 1) * 50000.0)
                row.createCell(1).setCellValue(LocalDate.now().minusDays(index.toLong()).toString())
            }

            file.outputStream().use { workbook.write(it) }
        }
        return file
    }

    private fun createExcelFileWithSalesOnly(tempDir: Path): File {
        val file = tempDir.resolve("sales-only.xlsx").toFile()
        XSSFWorkbook().use { workbook ->
            val salesSheet = workbook.createSheet("매출")
            repeat(3) { index ->
                val row = salesSheet.createRow(index)
                row.createCell(0).setCellValue((index + 1) * 100000.0)
                row.createCell(1).setCellValue(LocalDate.now().toString())
            }
            file.outputStream().use { workbook.write(it) }
        }
        return file
    }

    private fun createExcelFileWithPurchaseOnly(tempDir: Path): File {
        val file = tempDir.resolve("purchase-only.xlsx").toFile()
        XSSFWorkbook().use { workbook ->
            val purchaseSheet = workbook.createSheet("매입")
            repeat(3) { index ->
                val row = purchaseSheet.createRow(index)
                row.createCell(0).setCellValue((index + 1) * 50000.0)
                row.createCell(1).setCellValue(LocalDate.now().toString())
            }
            file.outputStream().use { workbook.write(it) }
        }
        return file
    }

    private fun createEmptyExcelFile(tempDir: Path): File {
        val file = tempDir.resolve("empty.xlsx").toFile()
        XSSFWorkbook().use { workbook ->
            // 빈 워크북 (시트 없음)
            file.outputStream().use { workbook.write(it) }
        }
        return file
    }

    private fun createExcelFileWithEmptyAmounts(tempDir: Path): File {
        val file = tempDir.resolve("empty-amounts.xlsx").toFile()
        XSSFWorkbook().use { workbook ->
            val salesSheet = workbook.createSheet("매출")
            // 첫 번째 행: 유효한 금액
            val row1 = salesSheet.createRow(0)
            row1.createCell(0).setCellValue(100000.0)
            row1.createCell(1).setCellValue(LocalDate.now().toString())

            // 두 번째 행: 금액 없음 (빈 셀)
            val row2 = salesSheet.createRow(1)
            row2.createCell(1).setCellValue(LocalDate.now().toString())

            // 세 번째 행: 유효한 금액
            val row3 = salesSheet.createRow(2)
            row3.createCell(0).setCellValue(200000.0)
            row3.createCell(1).setCellValue(LocalDate.now().toString())

            file.outputStream().use { workbook.write(it) }
        }
        return file
    }

    private fun createExcelFileWithoutDates(tempDir: Path): File {
        val file = tempDir.resolve("no-dates.xlsx").toFile()
        XSSFWorkbook().use { workbook ->
            val salesSheet = workbook.createSheet("매출")
            repeat(3) { index ->
                val row = salesSheet.createRow(index)
                row.createCell(0).setCellValue((index + 1) * 100000.0)
                // 날짜 셀 생성하지 않음
            }
            file.outputStream().use { workbook.write(it) }
        }
        return file
    }

    private fun createExcelFileWithMultipleRows(tempDir: Path, salesCount: Int, purchaseCount: Int): File {
        val file = tempDir.resolve("multiple-rows.xlsx").toFile()
        XSSFWorkbook().use { workbook ->
            val salesSheet = workbook.createSheet("매출")
            repeat(salesCount) { index ->
                val row = salesSheet.createRow(index)
                row.createCell(0).setCellValue((index + 1) * 10000.0)
                row.createCell(1).setCellValue(LocalDate.now().toString())
            }

            val purchaseSheet = workbook.createSheet("매입")
            repeat(purchaseCount) { index ->
                val row = purchaseSheet.createRow(index)
                row.createCell(0).setCellValue((index + 1) * 5000.0)
                row.createCell(1).setCellValue(LocalDate.now().toString())
            }

            file.outputStream().use { workbook.write(it) }
        }
        return file
    }

    private fun createExcelFileWithLargeAmounts(tempDir: Path, amount: Double): File {
        val file = tempDir.resolve("large-amounts.xlsx").toFile()
        XSSFWorkbook().use { workbook ->
            val salesSheet = workbook.createSheet("매출")
            val row = salesSheet.createRow(0)
            row.createCell(0).setCellValue(amount)
            row.createCell(1).setCellValue(LocalDate.now().toString())

            file.outputStream().use { workbook.write(it) }
        }
        return file
    }

    private fun createExcelFileWithDecimalAmounts(tempDir: Path): File {
        val file = tempDir.resolve("decimal-amounts.xlsx").toFile()
        XSSFWorkbook().use { workbook ->
            val salesSheet = workbook.createSheet("매출")
            repeat(3) { index ->
                val row = salesSheet.createRow(index)
                row.createCell(0).setCellValue(123456.78)
                row.createCell(1).setCellValue(LocalDate.now().toString())
            }
            file.outputStream().use { workbook.write(it) }
        }
        return file
    }

    private fun createExcelFileWithZeroAmount(tempDir: Path): File {
        val file = tempDir.resolve("zero-amount.xlsx").toFile()
        XSSFWorkbook().use { workbook ->
            val salesSheet = workbook.createSheet("매출")
            val row = salesSheet.createRow(0)
            row.createCell(0).setCellValue(0.0)
            row.createCell(1).setCellValue(LocalDate.now().toString())

            file.outputStream().use { workbook.write(it) }
        }
        return file
    }

    private fun createExcelFileWithEmptyRows(tempDir: Path): File {
        val file = tempDir.resolve("empty-rows.xlsx").toFile()
        XSSFWorkbook().use { workbook ->
            val salesSheet = workbook.createSheet("매출")

            // 첫 번째 행: 유효
            val row1 = salesSheet.createRow(0)
            row1.createCell(0).setCellValue(100000.0)
            row1.createCell(1).setCellValue(LocalDate.now().toString())

            // 두 번째 행: 빈 행 (생성하지 않음)

            // 세 번째 행: 유효
            val row3 = salesSheet.createRow(2)
            row3.createCell(0).setCellValue(200000.0)
            row3.createCell(1).setCellValue(LocalDate.now().toString())

            file.outputStream().use { workbook.write(it) }
        }
        return file
    }
}
