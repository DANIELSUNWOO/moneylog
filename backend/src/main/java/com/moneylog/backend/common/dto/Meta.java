package com.moneylog.backend.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import org.springframework.data.domain.Page;

@Getter
public class Meta {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final PageMeta pagination;

    private Meta(PageMeta pagination) {
        this.pagination = pagination;
    }

    public static Meta of(Page<?> page) {
        return new Meta(PageMeta.from(page));
    }
}
