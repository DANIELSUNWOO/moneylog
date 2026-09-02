package com.moneylog.backend.common.dto;

import lombok.Getter;
import org.springframework.data.domain.Page;

/** API 명세 0-1의 meta.pagination. 프론트가 페이지 버튼을 그리는 데 필요한 것만 담는다. */
@Getter
public class PageMeta {
    private final int page;
    private final int size;
    private final long totalItems;
    private final int totalPages;
    private final boolean hasNext;
    private final boolean hasPrev;

    private PageMeta(int page, int size, long totalItems, int totalPages, boolean hasNext, boolean hasPrev) {
        this.page = page;
        this.size = size;
        this.totalItems = totalItems;
        this.totalPages = totalPages;
        this.hasNext = hasNext;
        this.hasPrev = hasPrev;
    }

    public static PageMeta from(Page<?> page) {
        return new PageMeta(
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext(),
                page.hasPrevious());
    }
}
