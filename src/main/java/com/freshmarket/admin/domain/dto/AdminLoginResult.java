package com.freshmarket.admin.domain.dto;

/*
 * AdminAuthService 와 AdminAuthController 사이에서만 쓴다. REST 응답으로 직렬화되지 않는다.
 * refreshToken 은 컨트롤러가 HttpOnly 쿠키를 만드는 데만 쓰고, response(실제 응답 본문)에는 담기지 않는다.
 */
public record AdminLoginResult(AdminLoginResponse response, String refreshToken, long refreshTokenValiditySeconds) {

    /*
     * refreshToken 은 여기서만 잠깐 원문으로 존재한다 (컨트롤러가 쿠키로 감싸는 순간 이 객체는 버려진다).
     * REST 응답으로 나가지 않는다고 해서 로그에서도 안전한 건 아니다.
     * 이 값을 손에 넣으면 액세스 토큰 유효기간이 끝난 뒤에도 재발급을 계속 받아 로그인 상태를 이어갈 수 있다 (SEC-4-02).
     * response 는 자체 toString() 에서 accessToken 을 이미 가리므로 그대로 위임한다.
     */
    @Override
    public String toString() {
        return "AdminLoginResult[response=" + response + ", refreshToken=****, refreshTokenValiditySeconds="
                + refreshTokenValiditySeconds + "]";
    }
}