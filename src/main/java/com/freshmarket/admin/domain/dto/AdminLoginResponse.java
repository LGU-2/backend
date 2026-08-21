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