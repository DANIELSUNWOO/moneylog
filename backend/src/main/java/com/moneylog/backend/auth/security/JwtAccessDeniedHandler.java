package com.moneylog.backend.auth.security;

import com.moneylog.backend.common.exception.CommonErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 403 — 인증은 됐지만 시큐리티 레벨에서 거부된 경우.
 * 우리 서비스는 "남의 리소스"를 404로 응답하므로 이 핸들러가 탈 일은 드물지만,
 * 시큐리티가 자체적으로 던지는 AccessDeniedException도 같은 형식으로 나가게 한다.
 */
@Component
@RequiredArgsConstructor
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final SecurityResponseWriter writer;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        writer.write(response, CommonErrorCode.FORBIDDEN);
    }
}
