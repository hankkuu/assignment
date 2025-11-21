package com.kcd.tax.common.enums

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class AdminRoleTest {

    @Test
    fun `ADMIN 역할이 존재해야 한다`() {
        val admin = AdminRole.ADMIN
        assertNotNull(admin)
        assertEquals("ADMIN", admin.name)
    }

    @Test
    fun `MANAGER 역할이 존재해야 한다`() {
        val manager = AdminRole.MANAGER
        assertNotNull(manager)
        assertEquals("MANAGER", manager.name)
    }

    @Test
    fun `모든 역할이 정의되어야 한다`() {
        val roles = AdminRole.entries
        assertEquals(2, roles.size)
        assertTrue(roles.contains(AdminRole.ADMIN))
        assertTrue(roles.contains(AdminRole.MANAGER))
    }

    @Test
    fun `문자열로부터 AdminRole을 생성할 수 있다`() {
        assertEquals(AdminRole.ADMIN, AdminRole.valueOf("ADMIN"))
        assertEquals(AdminRole.MANAGER, AdminRole.valueOf("MANAGER"))
    }
}
