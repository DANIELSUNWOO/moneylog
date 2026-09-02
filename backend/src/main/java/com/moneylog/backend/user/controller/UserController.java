package com.moneylog.backend.user.controller;

import com.moneylog.backend.common.dto.ApiResponse;
import com.moneylog.backend.user.dto.UserResponse;
import com.moneylog.backend.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "사용자", description = "로그인한 사용자 본인 정보")
public class UserController {

    private final UserService userService;

    @Operation(summary = "내 정보 조회", description = "새로고침 후 토큰만 남았을 때 로그인 상태를 복구하는 용도.")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> me(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success("내 정보를 조회했습니다.", userService.findMe(userId)));
    }
}
