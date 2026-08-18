package com.freshmarket.admin.domain.controller;

import com.freshmarket.admin.domain.dto.AdminLoginRequest;
import com.freshmarket.admin.domain.dto.AdminLoginResponse;
import com.freshmarket.admin.domain.dto.AdminLoginResult;
import com.freshmarket.admin.domain.service.AdminAuthService;
import com.freshmarket.common.response.ResponseEnvelope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/*
 * 리소스를 tokens 로 둔다 (auth.md). 서버가 실제로 보관하는 것이 리프레시 토큰(해시)이지,
 * "세션"이라는 실체는 DB 어디에도 없다 — sessions 로 모델링했던 이전 설계를 auth.md 기준으로 되돌린다.
 *
 * :refresh, DELETE(로그아웃), PUT .../password 는 이 PR 범위가 아니다 (별도 PR).
 */
@Tag(name = "관리자 인증", description = "관리자 로그인/로그아웃/토큰 재발급")
@RestController
@RequestMapping("${app.security.admin-auth-path}")
class AdminAuthController {

    // auth.md: Set-Cookie: refreshToken=...; HttpOnly; Secure; SameSite=Strict; Path=...
    private static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";

    private final AdminAuthService adminAuthService;
    private final String adminAuthPath;
    private final boolean refreshCookieSecure;

    AdminAuthController(
            AdminAuthService adminAuthService,
            @Value("${app.security.admin-auth-path}") String adminAuthPath,
            @Value("${app.jwt.admin.refresh-cookie-secure}") boolean refreshCookieSecure) {
        this.adminAuthService = adminAuthService;
        this.adminAuthPath = adminAuthPath;
        this.refreshCookieSecure = refreshCookieSecure;
    }

    @Operation(
            summary = "관리자 로그인",
            description = "아이디와 비밀번호로 인증해 관리자 토큰을 발급한다. 액세스 토큰은 응답 본문으로, "
                    + "리프레시 토큰은 HttpOnly 쿠키로 내려간다. 5회 실패 시 잠금 정책은 이번 범위에 포함하지 않는다."
    )
    @ApiResponse(responseCode = "201", description = "발급 성공")
    @ApiResponse(responseCode = "401", description = "아이디 또는 비밀번호 불일치. 사유를 구분해 알리지 않는다 (ADMIN-001)")
    @ApiResponse(responseCode = "403", description = "비활성 계정 (ADMIN-002)")
    @PostMapping
    ResponseEntity<ResponseEnvelope<AdminLoginResponse>> login(
            @Valid @RequestBody AdminLoginRequest request) {
        AdminLoginResult result = adminAuthService.login(request);

        /*
         * auth.md "관리자 > 재발급과 로그아웃" 절의 쿠키 그대로다.
         * Path 를 로그인/재발급/로그아웃이 공유하는 이 경로 하나로 좁힌다 — 다른 admin API 요청에는 이 쿠키가 실려 가지 않는다.
         * Secure 는 설정으로 뺐다: 로컬 개발(HTTP)에서는 꺼야 브라우저가 쿠키를 실어 보낸다.
         */
        ResponseCookie refreshTokenCookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, result.refreshToken())
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite("Strict")
                .path(adminAuthPath)
                .maxAge(result.refreshTokenValiditySeconds())
                .build();

        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString())
                .body(ResponseEnvelope.success(result.response()));
    }
}