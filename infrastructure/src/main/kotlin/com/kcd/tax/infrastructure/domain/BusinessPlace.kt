package com.kcd.tax.infrastructure.domain

import com.kcd.tax.common.enums.CollectionStatus
import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime

/**
 * 사업장 엔티티
 */
@Entity
@Table(
    name = "business_place",
    indexes = [
        Index(name = "idx_business_place_status", columnList = "collection_status")
    ]
)
@EntityListeners(AuditingEntityListener::class)
class BusinessPlace(
    @Id
    @Column(name = "business_number", length = 10, nullable = false)
    val businessNumber: String,

    @Column(nullable = false, length = 100)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "collection_status", nullable = false, length = 20)
    var collectionStatus: CollectionStatus = CollectionStatus.NOT_REQUESTED,

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    /**
     * 수집 시작
     * NOT_REQUESTED 상태에서만 호출 가능
     */
    fun startCollection() {
        require(collectionStatus == CollectionStatus.NOT_REQUESTED) {
            "수집은 NOT_REQUESTED 상태에서만 시작할 수 있습니다. 현재 상태: $collectionStatus"
        }
        collectionStatus = CollectionStatus.COLLECTING
    }

    /**
     * 수집 완료
     * COLLECTING 상태에서만 호출 가능
     */
    fun completeCollection() {
        require(collectionStatus == CollectionStatus.COLLECTING) {
            "COLLECTING 상태에서만 완료할 수 있습니다. 현재 상태: $collectionStatus"
        }
        collectionStatus = CollectionStatus.COLLECTED
    }

    /**
     * 수집 실패 시 상태 복원
     */
    fun resetCollection() {
        collectionStatus = CollectionStatus.NOT_REQUESTED
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BusinessPlace) return false
        return businessNumber == other.businessNumber
    }

    override fun hashCode(): Int {
        return businessNumber.hashCode()
    }

    override fun toString(): String {
        return "BusinessPlace(businessNumber='$businessNumber', name='$name', status=$collectionStatus)"
    }
}
