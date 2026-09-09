package com.moneylog.backend.statistics.service;

import com.moneylog.backend.common.domain.MonthRange;
import com.moneylog.backend.common.domain.TransactionType;
import com.moneylog.backend.statistics.dto.CategorySumResponse;
import com.moneylog.backend.statistics.dto.MonthlyStatisticsResponse;
import com.moneylog.backend.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final TransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public MonthlyStatisticsResponse monthly(Long userId, String yearMonth) {
        MonthRange range = MonthRange.of(yearMonth);

        long income = 0L;
        long expense = 0L;

        // [type, sum] 형태로 최대 2행이 돌아온다. 거래가 없는 달은 0행.
        for (Object[] row : transactionRepository.sumAmountByType(userId, range.start(), range.end())) {
            TransactionType type = (TransactionType) row[0];
            long sum = ((Number) row[1]).longValue();
            if (type == TransactionType.INCOME) {
                income = sum;
            } else {
                expense = sum;
            }
        }

        // 수입은 카테고리가 1~2개라 집계 의미가 적어 지출만 낸다.
        List<CategorySumResponse> byCategory =
                transactionRepository.sumAmountByCategory(userId, TransactionType.EXPENSE, range.start(), range.end());

        return new MonthlyStatisticsResponse(range.yearMonth().toString(), income, expense, income - expense, byCategory);
    }
}
