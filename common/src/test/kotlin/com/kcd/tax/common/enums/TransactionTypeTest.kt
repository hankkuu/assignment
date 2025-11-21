package com.kcd.tax.common.enums

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class TransactionTypeTest {

    @Test
    fun `모든 거래 타입이 정의되어야 한다`() {
        val types = TransactionType.entries
        assertEquals(2, types.size)
        assertTrue(types.contains(TransactionType.SALES))
        assertTrue(types.contains(TransactionType.PURCHASE))
    }

    @Test
    fun `SALES 타입이 존재해야 한다`() {
        val type = TransactionType.SALES
        assertNotNull(type)
        assertEquals("SALES", type.name)
    }

    @Test
    fun `PURCHASE 타입이 존재해야 한다`() {
        val type = TransactionType.PURCHASE
        assertNotNull(type)
        assertEquals("PURCHASE", type.name)
    }

    @Test
    fun `문자열로부터 TransactionType을 생성할 수 있다`() {
        assertEquals(TransactionType.SALES, TransactionType.valueOf("SALES"))
        assertEquals(TransactionType.PURCHASE, TransactionType.valueOf("PURCHASE"))
    }
}
