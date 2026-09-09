package com.moneylog.backend.authorization;

import com.moneylog.backend.support.ApiTestSupport;
import com.moneylog.backend.user.dto.SignupRequest;
import com.moneylog.backend.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 인가(내 것만 접근) 이전에 인증(로그인했는가)이 먼저 서 있어야 한다.
 * 프론트의 ProtectedRoute는 화면 가드일 뿐이고, 누구든 curl로 API를 직접 부를 수 있으므로
 * 실제 방어선이 서버에 있다는 것을 여기서 확인한다.
 */
@DisplayName("인증 (F-01)")
class AuthenticationTest extends ApiTestSupport {

    @Test
    @DisplayName("토큰 없이 보호된 API를 부르면 401이다")
    void protectedApi_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/transactions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("위조된 토큰이면 401이다 — 서명 검증이 실제로 동작한다")
    void protectedApi_withForgedToken_returns401() throws Exception {
        mockMvc.perform(get("/api/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not.a.real.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("정상 토큰이면 통과한다")
    void protectedApi_withValidToken_returns200() throws Exception {
        User user = createUser("me@moneylog.test");

        mockMvc.perform(get("/api/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearerOf(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("회원가입은 토큰 없이 열려 있고, 가입과 동시에 기본 카테고리 6개가 생긴다 (F-02)")
    void signup_isPublic_andSeedsDefaultCategories() throws Exception {
        SignupRequest request = new SignupRequest("new@moneylog.test", "password1234", "새사용자");

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("new@moneylog.test"));

        assertThat(categoryRepository.count()).isEqualTo(6);
    }

    @Test
    @DisplayName("비밀번호는 어떤 응답에도 실려 나가지 않는다")
    void signup_responseNeverContainsPassword() throws Exception {
        SignupRequest request = new SignupRequest("new@moneylog.test", "password1234", "새사용자");

        String body = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).doesNotContain("password1234");
        assertThat(body).doesNotContain("\"password\"");
    }
}
