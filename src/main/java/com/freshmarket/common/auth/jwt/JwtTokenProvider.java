package com.freshmarket.common.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Access/Refresh 토큰 생성·파싱·검증 담당. member/admin 공용 인증 인프라라 common.auth 소속.
 * role 클레임은 Spring Security 권한 문자열 그대로("ROLE_USER", "ROLE_ADMIN", "ROLE_SUPER_ADMIN")를
 * 담는다 — MemberRole.name(), AdminRole.toAuthority() 양쪽 다 이 포맷으로 맞춰뒀다.
 */
@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long accessTokenValidityMs;
    private final long refreshTokenValidityMs;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-validity-ms}") long accessTokenValidityMs,
            @Value("${jwt.refresh-token-validity-ms}") long refreshTokenValidityMs
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.accessTokenValidityMs = accessTokenValidityMs;
        this.refreshTokenValidityMs = refreshTokenValidityMs;
    }

    public String createAccessToken(Long id, TokenType type, String role) {
        return Jwts.builder()
                .subject(String.valueOf(id))
                .claim("type", type.name())
                .claim("role", role)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + accessTokenValidityMs))
                .signWith(secretKey)
                .compact();
    }

    // TODO(2026-08-18, opaque refresh token 전환): 리프레시 토큰은 재발급 때마다 어차피
    // RefreshTokenRepository의 Redis CAS를 거치므로 "저장소 없이 서명만으로 검증 가능"이라는
    // JWT의 이점을 못 챙기면서, 서명만 되고 암호화는 안 된 페이로드라 쿠키가 어떤 식으로든
    // 읽히면 memberId/role/type이 평문으로 새는 리스크만 진다. 팀원이 만든
    // OpaqueTokenGenerator(SecureRandom 32바이트 + URL-safe Base64)로 바꾸는 쪽으로 방향을
    // 잡았다 — 커밋 이후 팀원 코드와 합칠 때 진행한다. accessToken은 그대로 JWT 유지(짧은
    // 수명 + 나중에 진짜 stateless 검증으로 최적화할 여지 때문). 바꿀 때 같이 손댈 곳:
    // RefreshTokenRepository(키를 role:id가 아니라 token 해시 기반으로), MemberTokenService
    // (issue/reissue), MemberAuthController.reissue()(클레임을 토큰에서 먼저 읽는 지금 순서를
    // "저장소 조회 후 알아냄"으로 뒤집어야 함).
    public String createRefreshToken(Long id, TokenType type, String role, boolean remember) {
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(id))
                .claim("type", type.name())
                .claim("role", role)
                .claim("remember", remember)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + refreshTokenValidityMs))
                .signWith(secretKey)
                .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(secretKey).build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Long getId(String token) {
        return Long.valueOf(parseClaims(token).getSubject());
    }

    public TokenType getType(String token) {
        String type = parseClaims(token).get("type", String.class);
        return type == null ? null : TokenType.valueOf(type);
    }

    public String getRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    public boolean getRemember(String token) {
        Boolean remember = parseClaims(token).get("remember", Boolean.class);
        return remember != null && remember;
    }

    public LocalDateTime getIssuedAt(String token) {
        Date issuedAt = parseClaims(token).getIssuedAt();
        return issuedAt == null ? null : LocalDateTime.ofInstant(issuedAt.toInstant(), ZoneId.systemDefault());
    }

    public String getJti(String token) {
        return parseClaims(token).getId();
    }

    public long getAccessTokenValidityMs() {
        return accessTokenValidityMs;
    }

    public long getRefreshTokenValidityMs() {
        return refreshTokenValidityMs;
    }
}
