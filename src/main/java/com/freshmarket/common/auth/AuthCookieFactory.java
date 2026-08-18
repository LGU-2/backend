package com.freshmarket.common.auth;

import com.freshmarket.common.auth.jwt.JwtTokenProvider;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

// (2026-08-18 11:05) com.example.freshdemo.common.auth에서 이식.
// (2026-08-18 12:45) docs/api/auth.md 기준으로 accessToken 쿠키를 없애고 헤더 방식으로 바꿨었다.
// (2026-08-18 16:20) 사용자 요청으로 다시 쿠키 방식으로 되돌림 — accessTokenCookie()/
// expiredAccessTokenCookie() 복원. 이제 accessToken도 HttpOnly 쿠키로 나가고 응답 본문엔
// 안 싣는다(MemberTokenResponse 참고) — 본문에도 실으면 XSS로 응답을 읽을 수 있는 스크립트가
// httpOnly와 무관하게 토큰을 그대로 얻어가므로 httpOnly의 의미가 없어진다.
// [주의] 이건 docs/api/auth.md가 명시한 "발급은 본문, 사용은 헤더" 계약과 다르다 — 문서를
// 같이 고칠지는 아직 정해지지 않았다.
// [주의] accessToken이 쿠키로 다시 나가면서 CSRF 노출 범위가 refreshToken 전용 좁은 경로가
// 아니라 인증이 필요한 전체 API로 넓어졌다. SameSite=Strict가 대부분을 막아주지만, 완전한
// 방어는 아니다 — CSRF 토큰 같은 별도 대책을 넣을지는 아직 결정되지 않았다.
/**
 * accessToken/refreshToken 쿠키를 만들고 지우는 로직을 한 곳에 모은 것.
 */
@Component
@RequiredArgsConstructor
public class AuthCookieFactory {

    private static final String REFRESH_TOKEN_COOKIE_PATH = "/api/v1/auth/tokens";
    // accessToken은 재발급/로그아웃 경로만이 아니라 인증이 필요한 모든 API 요청에 실려야 한다.
    private static final String ACCESS_TOKEN_COOKIE_PATH = "/";

    private final JwtTokenProvider jwtTokenProvider;

    @Value("${jwt.cookie.secure:false}")
    private boolean secure; // TODO: 운영(https)에서는 반드시 true

    public ResponseCookie accessTokenCookie(String accessToken) {
        return ResponseCookie.from("accessToken", accessToken)
                .httpOnly(true)
                .secure(secure)
                .path(ACCESS_TOKEN_COOKIE_PATH)
                .sameSite("Strict")
                .maxAge(Duration.ofMillis(jwtTokenProvider.getAccessTokenValidityMs()))
                .build();
    }

    public ResponseCookie expiredAccessTokenCookie() {
        return ResponseCookie.from("accessToken", "")
                .httpOnly(true)
                .secure(secure)
                .path(ACCESS_TOKEN_COOKIE_PATH)
                .maxAge(Duration.ZERO)
                .sameSite("Strict")
                .build();
    }

    public ResponseCookie refreshTokenCookie(String refreshToken, boolean persistent) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .secure(secure)
                .path(REFRESH_TOKEN_COOKIE_PATH)
                .sameSite("Strict");
        if (persistent) {
            builder.maxAge(Duration.ofMillis(jwtTokenProvider.getRefreshTokenValidityMs()));
        }
        return builder.build();
    }

    public ResponseCookie expiredRefreshTokenCookie() {
        return ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(secure)
                .path(REFRESH_TOKEN_COOKIE_PATH)
                .maxAge(Duration.ZERO)
                .sameSite("Strict")
                .build();
    }
}
