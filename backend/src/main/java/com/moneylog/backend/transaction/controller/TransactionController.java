package com.moneylog.backend.transaction.controller;

import com.moneylog.backend.common.domain.TransactionType;
import com.moneylog.backend.common.dto.ApiResponse;
import com.moneylog.backend.common.dto.Meta;
import com.moneylog.backend.transaction.dto.TransactionRequest;
import com.moneylog.backend.transaction.dto.TransactionResponse;
import com.moneylog.backend.transaction.entity.Transaction;
import com.moneylog.backend.transaction.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@Tag(name = "거래내역", description = "수입/지출 등록·조회·수정·삭제. 모든 조회는 로그인 사용자 기준으로 필터링된다.")
public class TransactionController {

    private final TransactionService transactionService;

    @Operation(summary = "거래 목록 조회",
            description = "월·타입·카테고리로 필터링하고 페이징한다. yearMonth 생략 시 이번 달. "
                    + "page/size/sort 파라미터를 그대로 쓸 수 있다(예: ?sort=amount,desc). 페이지 정보는 meta.pagination에 담긴다.")
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, List<TransactionResponse>>>> findAll(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) String yearMonth,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) Long categoryId,
            @PageableDefault(size = 20, sort = "transactionDate", direction = Sort.Direction.DESC)
            Pageable pageable) {

        Page<Transaction> result = transactionService.findAll(
                userId, yearMonth, type, categoryId, pageable);

        List<TransactionResponse> transactions = result.getContent().stream()
                .map(TransactionResponse::from)
                .toList();

        return ResponseEntity.ok(ApiResponse.success(
                "거래내역 목록을 조회했습니다.",
                Map.of("transactions", transactions),
                Meta.of(result)));
    }

    @Operation(summary = "거래 등록",
            description = "카테고리가 내 것인지, 그리고 카테고리 타입과 거래 타입이 일치하는지 함께 검증한다.")
    @PostMapping
    public ResponseEntity<ApiResponse<TransactionResponse>> create(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody TransactionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("거래내역이 등록되었습니다.",
                        transactionService.create(userId, request)));
    }

    @Operation(summary = "거래 상세 조회", description = "내 거래가 아니면 404를 반환한다.")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TransactionResponse>> findOne(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("거래내역을 조회했습니다.",
                transactionService.findOne(userId, id)));
    }

    @Operation(summary = "거래 수정", description = "전체 교체(PUT). 등록과 같은 바디를 받는다.")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TransactionResponse>> update(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @Valid @RequestBody TransactionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("거래내역이 수정되었습니다.",
                transactionService.update(userId, id, request)));
    }

    @Operation(summary = "거래 삭제")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Object>> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {
        transactionService.delete(userId, id);
        return ResponseEntity.ok(ApiResponse.success("거래내역이 삭제되었습니다.", null));
    }
}
