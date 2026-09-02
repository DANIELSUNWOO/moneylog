package com.moneylog.backend.category.entity;

import com.moneylog.backend.common.domain.TransactionType;
import com.moneylog.backend.common.entity.BaseTimeEntity;
import com.moneylog.backend.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "categories",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_categories_user_name_type",
                columnNames = {"user_id", "name", "type"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_categories_user"))
    private User user;

    @Column(nullable = false, length = 50)
    private String name;

    /** ORDINAL로 저장하면 enum 순서가 바뀔 때 데이터가 뒤섞인다. 반드시 STRING. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransactionType type;

    @Builder
    public Category(User user, String name, TransactionType type) {
        this.user = user;
        this.name = name;
        this.type = type;
    }

    public void update(String name, TransactionType type) {
        this.name = name;
        this.type = type;
    }
}
