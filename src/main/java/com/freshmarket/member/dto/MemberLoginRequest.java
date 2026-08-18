package com.freshmarket.member.dto;

import jakarta.validation.constraints.NotBlank;

// (2026-08-18 12:35) docs/api/auth.md의 POST /v1/auth/tokens 요청 본문. remember는 문서
// 표에는 없지만 fresh-demo 원본의 "자동로그인" 개념(RememberMeRequestFilter)을 프론트 콜백형에
// 맞게 옮긴 것 — 카카오 인가 요청 시작 시점 쿼리파라미터 대신, 로그인 완료 요청 본문에 직접 싣는다.
public record MemberLoginRequest(
        @NotBlank String authorizationCode,
        @NotBlank String state,
        boolean remember
) {
}
