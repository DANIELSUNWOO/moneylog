package com.moneylog.backend.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneylog.backend.common.dto.ApiResponse;
import com.moneylog.backend.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 시큐리티 필터 단계에서 나는 401/403을 공통 응답 형식으로 직접 써준다.
 * 이 단계는 아직 DispatcherServlet 이전이라 @RestControllerAdvice가 잡지 못한다.
 */
@Component
@RequiredArgsConstructor
public class SecurityResponseWriter {

    private final ObjectMapper objectMapper;

    public void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(),
                ApiResponse.error(errorCode.name(), errorCode.getMessage()));
    }
}
