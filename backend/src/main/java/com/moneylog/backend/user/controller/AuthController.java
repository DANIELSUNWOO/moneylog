package com.moneylog.backend.user.controller;

import com.moneylog.backend.common.dto.ApiResponse;
import com.moneylog.backend.user.dto.LoginRequest;
import com.moneylog.backend.user.dto.LoginResponse;
import com.moneylog.backend.user.dto.SignupRequest;
import com.moneylog.backend.user.dto.UserResponse;
import com.moneylog.backend.user.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "인증", description = "회원가입 / 로그인. 이 두 API만 토큰 없이 호출할 수 있다.")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "회원가입", description = "이메일·비밀번호·닉네임으로 가입한다. 가입과 동시에 기본 카테고리 6개가 생성된다.")
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<UserResponse>> signup(@Valid @RequestBody SignupRequest request) {
        UserResponse response = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("회원가입이 완료되었습니다.", response));
    }

    @Operation(summary = "로그인", description = "성공 시 accessToken을 발급한다. 이후 요청은 Authorization: Bearer {token} 헤더로 보낸다.")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success("로그인에 성공했습니다.", authService.login(request)));
    }
}
