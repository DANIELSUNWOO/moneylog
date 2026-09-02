package com.moneylog.backend.category.dto;

import com.moneylog.backend.category.entity.Category;
import com.moneylog.backend.common.domain.TransactionType;

public record CategoryResponse(Long id, String name, TransactionType type) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getType());
    }
}
