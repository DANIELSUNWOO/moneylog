package com.moneylog.backend.common.domain;

import com.moneylog.backend.common.exception.BusinessException;
import com.moneylog.backend.common.exception.CommonErrorCode;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

/**
 * "yyyy-MM" 문자열을 조회 대상 월의 [시작일, 종료일] 범위로 바꾼다.
 *
 * 거래 목록(F-04)과 월별 통계(F-05)가 같은 규칙으로 월을 해석해야 한다.
 * 두 서비스에 같은 파싱 코드를 각각 두면 한쪽만 고치는 사고가 생기므로 여기로 모았다.
 */
public record MonthRange(YearMonth yearMonth, LocalDate start, LocalDate end) {

    /** yearMonth가 비어 있으면 이번 달로 본다. */
    public static MonthRange of(String yearMonth) {
        YearMonth ym = parse(yearMonth);
        return new MonthRange(ym, ym.atDay(1), ym.atEndOfMonth());
    }

    private static YearMonth parse(String yearMonth) {
        if (yearMonth == null || yearMonth.isBlank()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(yearMonth);
        } catch (DateTimeParseException e) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    "yearMonth 형식이 올바르지 않습니다. (예: 2026-09)");
        }
    }
}
