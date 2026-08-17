package com.freshmarket.common.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/*
 * 서비스 자체 JWT 발급만 담당한다. type=ADMIN, role=... 같은 클레임의 의미는 이 클래스가 모른다.
 * 클레임을 무엇으로 채울지는 호출하는 도메인이 결정한다 (common 은 도메인을 모른다, DPB-5-03).
 *
 * 검증(파싱) 책임은 아직 없다. 보호된 관리자 API가 생기는 PR에서 토큰을 검사하는 필터와 함께 파싱 메서드를 추가한다.
 */
@Component
public class JwtTokenProvider {

    private final SecretKey key;

    public JwtTokenProvider(@Value("${app.jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String createToken(String subject, Map<String, Object> claims, Duration validity) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .claims(claims)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(validity)))
                .signWith(key)
                .compact();
    }
}