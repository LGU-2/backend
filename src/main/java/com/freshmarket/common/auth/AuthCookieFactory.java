package com.freshmarket.common.auth;

import com.freshmarket.common.auth.jwt.JwtTokenProvider;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

// accessToken/refreshToken 둘 다 HttpOnly 쿠키로 내려준다 — 응답 본문에는 토큰 문자열을
// 싣지 않는다(MemberTokenResponse 참고). 본문에 실으면 그 응답을 읽는 스크립트가 httpOnly
// 여부와 무관하게 토큰을 그대로 얻어갈 수 있어 httpOnly로 얻는 XSS 방어 효과가 없어진다.
// 대신 accessToken도 쿠키인 이상 CSRF 노출 범위가 인증이 필요한 전체 API로 넓어진다 —
// SameSite=Strict가 대부분을 막아주지만 완전한 방어는 아니다. CSRF 토큰(더블서밋 쿠키 등)
// 도입 여부는 docs/api/auth.md의 "정하지 못한 것"에 열린 채로 남아 있다.
/**
 * accessToken/refreshToken 쿠키를 만들고 지우는 로직을 한 곳에 모은 것.
 */
@Component
@RequiredArgsConstructor
public class AuthCookieFactory {

    // MemberAuthController가 실제로 매핑된 경로(/v1/auth/tokens, /v1/auth/tokens:refresh)와
    // 반드시 같아야 한다 — 여기가 어긋나면 브라우저가 이 쿠키를 그 요청에 자동으로 실어 보내지
    // 않아서 재발급/로그아웃이 항상 "리프레시 토큰 없음"으로 실패한다.
    private static final String REFRESH_TOKEN_COOKIE_PATH = "/v1/auth/tokens";
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
