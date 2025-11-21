package com.kcd.tax.infrastructure.domain

import com.kcd.tax.common.enums.TransactionType
import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 거래내역 엔티티 (매출/매입)
 */
@Entity
@Table(
    name = "transaction",
    indexes = [
        Index(name = "idx_transaction_business", columnList = "business_number"),
        Index(name = "idx_transaction_date", columnList = "transaction_date"),
        Index(name = "idx_transaction_type", columnList = "type"),
        Index(name = "idx_transaction_business_type", columnList = "business_number,type")
    ]
)
@EntityListeners(AuditingEntityListener::class)
class Transaction(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "business_number", nullable = false, length = 10)
    val businessNumber: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val type: TransactionType,

    @Column(nullable = false, precision = 15, scale = 2)
    val amount: BigDecimal,

    @Column(name = "vat_amount", precision = 15, scale = 2)
    val vatAmount: BigDecimal? = null,

    @Column(name = "counterparty_name", length = 100)
    val counterpartyName: String? = null,

    @Column(name = "transaction_date", nullable = false)
    val transactionDate: LocalDate,

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    init {
        require(amount >= BigDecimal.ZERO) {
            "거래 금액은 0 이상이어야 합니다. amount: $amount"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Transaction) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int {
        return id?.hashCode() ?: 0
    }

    override fun toString(): String {
        return "Transaction(id=$id, businessNumber='$businessNumber', type=$type, amount=$amount, date=$transactionDate)"
    }
}
