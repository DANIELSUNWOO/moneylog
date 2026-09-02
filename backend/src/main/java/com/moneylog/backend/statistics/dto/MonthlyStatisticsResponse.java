package com.moneylog.backend.statistics.dto;

import java.util.List;

/** balance는 서버가 계산해서 내려준다. 프론트가 다시 계산하면 화면마다 값이 갈릴 수 있다. */
public record MonthlyStatisticsResponse(
        String yearMonth,
        long income,
        long expense,
        long balance,
        List<CategorySumResponse> byCategory
) {
}
