package com.moneylog.backend.statistics.service;

import com.moneylog.backend.common.domain.TransactionType;
import com.moneylog.backend.common.exception.BusinessException;
import com.moneylog.backend.common.exception.CommonErrorCode;
import com.moneylog.backend.statistics.dto.CategorySumResponse;
import com.moneylog.backend.statistics.dto.MonthlyStatisticsResponse;
import com.moneylog.backend.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final TransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public MonthlyStatisticsResponse monthly(Long userId, String yearMonth) {
        YearMonth ym = parseYearMonth(yearMonth);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();

        long income = 0L;
        long expense = 0L;

        // [type, sum] 형태로 최대 2행이 돌아온다. 거래가 없는 달은 0행.
        for (Object[] row : transactionRepository.sumAmountByType(userId, start, end)) {
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
                transactionRepository.sumAmountByCategory(userId, TransactionType.EXPENSE, start, end);

        return new MonthlyStatisticsResponse(ym.toString(), income, expense, income - expense, byCategory);
    }

    private YearMonth parseYearMonth(String yearMonth) {
        if (yearMonth == null || yearMonth.isBlank()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(yearMonth);
        } catch (Exception e) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    "yearMonth 형식이 올바르지 않습니다. (예: 2026-09)");
        }
    }
}
