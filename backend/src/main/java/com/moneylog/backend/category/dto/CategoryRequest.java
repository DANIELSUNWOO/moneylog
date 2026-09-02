package com.moneylog.backend.category.dto;

import com.moneylog.backend.common.domain.TransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank(message = "카테고리 이름은 필수입니다.")
        @Size(max = 50, message = "카테고리 이름은 50자를 넘을 수 없습니다.")
        String name,

        @NotNull(message = "타입은 필수입니다. (INCOME 또는 EXPENSE)")
        TransactionType type
) {
}
