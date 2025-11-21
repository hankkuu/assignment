package com.kcd.tax.infrastructure.domain

import com.kcd.tax.common.enums.AdminRole
import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime

/**
 * 관리자 엔티티
 */
@Entity
@Table(
    name = "admin",
    indexes = [
        Index(name = "idx_admin_username", columnList = "username"),
        Index(name = "idx_admin_role", columnList = "role")
    ]
)
@EntityListeners(AuditingEntityListener::class)
class Admin(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, unique = true, length = 50)
    val username: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val role: AdminRole,

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    /**
     * ADMIN 권한 여부 확인
     */
    fun isAdmin(): Boolean = role == AdminRole.ADMIN

    /**
     * MANAGER 권한 여부 확인
     */
    fun isManager(): Boolean = role == AdminRole.MANAGER

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Admin) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int {
        return id?.hashCode() ?: 0
    }

    override fun toString(): String {
        return "Admin(id=$id, username='$username', role=$role)"
    }
}
