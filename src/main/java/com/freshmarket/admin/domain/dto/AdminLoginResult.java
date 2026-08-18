package com.freshmarket.admin.domain.dto;

/*
 * AdminAuthService 와 AdminAuthController 사이에서만 쓴다. REST 응답으로 직렬화되지 않는다.
 * refreshToken 은 컨트롤러가 HttpOnly 쿠키를 만드는 데만 쓰고, response(실제 응답 본문)에는 담기지 않는다.
 */
public record AdminLoginResult(AdminLoginResponse response, String refreshToken, long refreshTokenValiditySeconds) {
}