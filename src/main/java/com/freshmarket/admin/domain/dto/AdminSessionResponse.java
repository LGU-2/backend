package com.freshmarket.admin.domain.dto;

import com.freshmarket.admin.domain.entity.AdminRole;

/*
 * refreshToken 은 지금은 응답 본문으로 내려준다.
 * "토큰 재발급" 기능(우선순위 하, 이번 범위 아님)이 생기면 HttpOnly 쿠키 전환을 다시 검토한다.
 */
public record AdminSessionResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        String refreshToken,
        AdminSummary admin
) {

    public record AdminSummary(
            Long adminId,
            String loginId,
            String name,
            AdminRole role
    ) {
    }
}