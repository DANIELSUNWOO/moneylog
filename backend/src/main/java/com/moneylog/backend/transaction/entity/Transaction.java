package com.moneylog.backend.transaction.entity;

import com.moneylog.backend.category.entity.Category;
import com.moneylog.backend.common.domain.TransactionType;
import com.moneylog.backend.common.entity.BaseTimeEntity;
import com.moneylog.backend.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "transactions", indexes = {
        // F-04 목록 조회와 F-05 통계의 주 경로
        @Index(name = "idx_tx_user_date", columnList = "user_id, transaction_date"),
        @Index(name = "idx_tx_user_category_date", columnList = "user_id, category_id, transaction_date")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Transaction extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_transactions_user"))
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_transactions_category"))
    private Category category;

    /**
     * category.type과 중복이지만 의도적으로 유지한다(erd.md D-1).
     * 통계·필터에서 JOIN 없이 집계하기 위함이며, 대신 서비스에서 일치를 반드시 검증한다.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransactionType type;

    /** 원 단위 정수. 항상 0보다 크다. */
    @Column(nullable = false)
    private Long amount;

    @Column(length = 255)
    private String description;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Builder
    public Transaction(User user, Category category, TransactionType type,
                       Long amount, String description, LocalDate transactionDate) {
        this.user = user;
        this.category = category;
        this.type = type;
        this.amount = amount;
        this.description = description;
        this.transactionDate = transactionDate;
    }

    public void update(Category category, TransactionType type, Long amount,
                       String description, LocalDate transactionDate) {
        this.category = category;
        this.type = type;
        this.amount = amount;
        this.description = description;
        this.transactionDate = transactionDate;
    }
}
