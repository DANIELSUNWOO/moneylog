package com.moneylog.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

// 우리는 JWT로만 인증하고 스프링 시큐리티의 기본 폼 로그인은 전혀 쓰지 않는다.
// 그런데 커스텀 UserDetailsService 빈이 없으면 스프링 부트가 "혹시 몰라서"
// 매 기동마다 임의의 비밀번호를 가진 인메모리 사용자를 하나 자동으로 만들고,
// 그 비밀번호를 콘솔에 남긴다("Using generated security password: ...").
// 이건 우리 서비스에서 쓰이지도 않는 계정이라 exclude로 아예 자동 생성을 끈다.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class BackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
