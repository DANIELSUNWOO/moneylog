package com.moneylog.backend.statistics.controller;

import com.moneylog.backend.common.dto.ApiResponse;
import com.moneylog.backend.statistics.dto.MonthlyStatisticsResponse;
import com.moneylog.backend.statistics.service.StatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/statistics")
@RequiredArgsConstructor
@Tag(name = "통계", description = "월별 수입·지출 집계")
public class StatisticsController {

    private final StatisticsService statisticsService;

    @Operation(summary = "월별 통계",
            description = "총수입·총지출·잔액과 카테고리별 지출 합계. yearMonth 생략 시 이번 달. 거래가 없으면 0과 빈 배열을 반환한다.")
    @GetMapping("/monthly")
    public ResponseEntity<ApiResponse<MonthlyStatisticsResponse>> monthly(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) String yearMonth) {
        return ResponseEntity.ok(ApiResponse.success("월별 통계를 조회했습니다.",
                statisticsService.monthly(userId, yearMonth)));
    }
}
