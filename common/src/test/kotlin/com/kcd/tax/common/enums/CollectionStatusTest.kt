package com.kcd.tax.common.enums

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class CollectionStatusTest {

    @Test
    fun `모든 상태가 정의되어야 한다`() {
        val statuses = CollectionStatus.entries
        assertEquals(3, statuses.size)
        assertTrue(statuses.contains(CollectionStatus.NOT_REQUESTED))
        assertTrue(statuses.contains(CollectionStatus.COLLECTING))
        assertTrue(statuses.contains(CollectionStatus.COLLECTED))
    }

    @Test
    fun `NOT_REQUESTED 상태가 존재해야 한다`() {
        val status = CollectionStatus.NOT_REQUESTED
        assertNotNull(status)
        assertEquals("NOT_REQUESTED", status.name)
    }

    @Test
    fun `COLLECTING 상태가 존재해야 한다`() {
        val status = CollectionStatus.COLLECTING
        assertNotNull(status)
        assertEquals("COLLECTING", status.name)
    }

    @Test
    fun `COLLECTED 상태가 존재해야 한다`() {
        val status = CollectionStatus.COLLECTED
        assertNotNull(status)
        assertEquals("COLLECTED", status.name)
    }

    @Test
    fun `문자열로부터 CollectionStatus를 생성할 수 있다`() {
        assertEquals(CollectionStatus.NOT_REQUESTED, CollectionStatus.valueOf("NOT_REQUESTED"))
        assertEquals(CollectionStatus.COLLECTING, CollectionStatus.valueOf("COLLECTING"))
        assertEquals(CollectionStatus.COLLECTED, CollectionStatus.valueOf("COLLECTED"))
    }
}
