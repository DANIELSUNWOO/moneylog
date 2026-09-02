package com.moneylog.backend.transaction.dto;

import com.moneylog.backend.common.domain.TransactionType;
import com.moneylog.backend.transaction.entity.Transaction;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * categoryId와 함께 categoryName을 평면으로 내려준다(api-spec D-7).
 * 이게 없으면 프론트가 목록 한 번 그리려고 카테고리 API를 또 부른다.
 */
public record TransactionResponse(
        Long id,
        TransactionType type,
        Long amount,
        Long categoryId,
        String categoryName,
        String description,
        LocalDate transactionDate,
        LocalDateTime createdAt
) {

    public static TransactionResponse from(Transaction t) {
        return new TransactionResponse(
                t.getId(), t.getType(), t.getAmount(),
                t.getCategory().getId(), t.getCategory().getName(),
                t.getDescription(), t.getTransactionDate(), t.getCreatedAt());
    }
}
