package com.freshmarket.admin.domain.dto;

import com.freshmarket.admin.domain.entity.AdminRole;
import io.swagger.v3.oas.annotations.media.Schema;

/*
 * refreshToken 은 지금은 응답 본문으로 내려준다.
 * "토큰 재발급" 기능(우선순위 하, 이번 범위 아님)이 생기면 HttpOnly 쿠키 전환을 다시 검토한다.
 *
 * adminId 는 담지 않는다 (IDS-7-01). 로그인 이후의 모든 인증된 요청은 JWT 로 식별되고,
 * 클라이언트가 adminId 를 다시 넘겨줄 일이 없다. 이 프로젝트가 아직 public_id(외부 노출 식별자)
 * 체계를 도입하지 않은 상태라, 내부 PK 를 그대로 내보내는 대신 아예 필드를 두지 않는 쪽을 골랐다.
 */
@Schema(description = "관리자 로그인 응답")
public record AdminSessionResponse(

        @Schema(description = "관리자 API 인증에 쓰는 액세스 토큰(JWT)")
        String accessToken,

        @Schema(description = "토큰 타입. 항상 Bearer 다", example = "Bearer")
        String tokenType,

        @Schema(description = "액세스 토큰 유효기간(초)", example = "1800")
        long expiresInSeconds,

        @Schema(description = "리프레시 토큰. 재발급 요청에 쓴다")
        String refreshToken,

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