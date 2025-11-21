package com.kcd.tax.infrastructure.domain

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime

/**
 * 사업장-관리자 권한 매핑 엔티티
 * N:M 관계를 표현
 */
@Entity
@Table(
    name = "business_place_admin",
    indexes = [
        Index(name = "idx_bpa_business", columnList = "business_number"),
        Index(name = "idx_bpa_admin", columnList = "admin_id")
    ],
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_business_admin",
            columnNames = ["business_number", "admin_id"]
        )
    ]
)
@EntityListeners(AuditingEntityListener::class)
class BusinessPlaceAdmin(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "business_number", nullable = false, length = 10)
    val businessNumber: String,

    @Column(name = "admin_id", nullable = false)
    val adminId: Long,

    @CreatedDate
    @Column(name = "granted_at", nullable = false, updatable = false)
    val grantedAt: LocalDateTime = LocalDateTime.now()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BusinessPlaceAdmin) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int {
        return id?.hashCode() ?: 0
    }

    override fun toString(): String {
        return "BusinessPlaceAdmin(id=$id, businessNumber='$businessNumber', adminId=$adminId)"
    }
}
