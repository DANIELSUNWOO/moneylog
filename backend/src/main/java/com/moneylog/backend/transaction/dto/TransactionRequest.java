package com.moneylog.backend.transaction.dto;

import com.moneylog.backend.common.domain.TransactionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** 등록과 수정이 같은 바디를 쓴다(PUT = 전체 교체, api-spec D-5). */
public record TransactionRequest(
        @NotNull(message = "타입은 필수입니다. (INCOME 또는 EXPENSE)")
        TransactionType type,

        @NotNull(message = "금액은 필수입니다.")
        @Positive(message = "금액은 0보다 커야 합니다.")
        Long amount,

        @NotNull(message = "카테고리는 필수입니다.")
        Long categoryId,

        @Size(max = 255, message = "설명은 255자를 넘을 수 없습니다.")
        String description,

        @NotNull(message = "거래일은 필수입니다.")
        LocalDate transactionDate
) {
}
