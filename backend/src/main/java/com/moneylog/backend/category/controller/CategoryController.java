package com.moneylog.backend.category.controller;

import com.moneylog.backend.category.dto.CategoryRequest;
import com.moneylog.backend.category.dto.CategoryResponse;
import com.moneylog.backend.category.service.CategoryService;
import com.moneylog.backend.common.domain.TransactionType;
import com.moneylog.backend.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@Tag(name = "카테고리", description = "거래를 분류하는 카테고리 관리")
public class CategoryController {

    private final CategoryService categoryService;

    @Operation(summary = "내 카테고리 목록", description = "type 파라미터로 수입/지출만 골라 볼 수 있다. 개수가 적어 페이징하지 않는다.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> findAll(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) TransactionType type) {
        return ResponseEntity.ok(ApiResponse.success("카테고리 목록을 조회했습니다.",
                categoryService.findAll(userId, type)));
    }

    @Operation(summary = "카테고리 추가")
    @PostMapping
    public ResponseEntity<ApiResponse<CategoryResponse>> create(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("카테고리가 추가되었습니다.",
                        categoryService.create(userId, request)));
    }

    @Operation(summary = "카테고리 수정")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CategoryResponse>> update(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(ApiResponse.success("카테고리가 수정되었습니다.",
                categoryService.update(userId, id, request)));
    }

    @Operation(summary = "카테고리 삭제", description = "거래가 등록된 카테고리는 삭제할 수 없다(409).")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Object>> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {
        categoryService.delete(userId, id);
        return ResponseEntity.ok(ApiResponse.success("카테고리가 삭제되었습니다.", null));
    }
}
