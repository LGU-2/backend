package com.freshmarket.admin.domain.dto;

import com.freshmarket.admin.domain.entity.AdminRole;
import io.swagger.v3.oas.annotations.media.Schema;

/*
 * 리프레시 토큰은 이 응답에 담지 않는다. AdminAuthController 가 HttpOnly 쿠키로 내려준다
 * (auth.md "토큰을 어떻게 전달하나" 절: 발급은 Set-Cookie, 사용은 브라우저가 자동 첨부).
 * JS 가 읽을 수 있는 응답 본문에 실으면 XSS 로 그대로 탈취될 수 있다.
 *
 * adminId 는 담지 않는다 (IDS-7-01). 로그인 이후의 모든 인증된 요청은 JWT 로 식별되고, 클라이언트가 adminId 를 다시 넘겨줄 일이 없다.
 */
@Schema(description = "관리자 로그인 응답")
public record AdminLoginResponse(

        @Schema(description = "관리자 API 인증에 쓰는 액세스 토큰(JWT)")
        String accessToken,

        @Schema(description = "토큰 타입. 항상 Bearer 다", example = "Bearer")
        String tokenType,

        @Schema(description = "액세스 토큰 유효기간(초)", example = "1800")
        long expiresInSeconds,

        @Schema(description = "로그인한 관리자 요약 정보")
        AdminSummary admin
) {

    /*
     * accessToken 은 그 자체로 API 를 호출할 수 있는 인증 수단이다.
     * 이 응답 객체가 로그에 찍히면 토큰이 평문으로 남아, 로그를 본 사람이 그 토큰으로
     * 유효기간이 끝나기 전까지 관리자 권한을 그대로 흉내 낼 수 있다 (SEC-4-02). 그래서 가린다.
     */
    @Override
    public String toString() {
        return "AdminLoginResponse[accessToken=****, tokenType=" + tokenType
                + ", expiresInSeconds=" + expiresInSeconds + ", admin=" + admin + "]";
    }

    @Schema(description = "로그인한 관리자 요약 정보")
    public record AdminSummary(

            @Schema(description = "관리자 로그인 아이디", example = "admin.kim")
            String loginId,

            @Schema(description = "관리자 이름", example = "김관리")
            String name,

            @Schema(description = "관리자 권한")
            AdminRole role
    ) {
    }
}