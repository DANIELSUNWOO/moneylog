package com.moneylog.backend.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/** JWT 발급과 검증만 담당한다. 토큰의 subject에 userId를 담는다. */
@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long accessTokenExpiration;

    public JwtTokenProvider(@Value("${jwt.secret}") String secret,
                            @Value("${jwt.access-token-expiration}") long accessTokenExpiration) {
        // 시크릿은 Base64로 인코딩된 값을 받는다(예: openssl rand -base64 32).
        // 문자열을 그대로 바이트로 쓰면 길이는 32여도 실제 엔트로피가 낮다. HS256은 32바이트 이상을 요구한다.
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.accessTokenExpiration = accessTokenExpiration;
    }

    public String createAccessToken(Long userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTokenExpiration))
                .signWith(key)
                .compact();
    }

    public Long getUserId(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
        return Long.valueOf(claims.getSubject());
    }

    public boolean isValid(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("유효하지 않은 토큰: {}", e.getMessage());
            return false;
        }
    }
}
