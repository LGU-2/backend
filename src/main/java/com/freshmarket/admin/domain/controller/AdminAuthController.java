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
 * 리소스를 tokens 로 둔다 (auth.md).
 * 서버가 실제로 보관하는 것은 Redis의 리프레시 토큰 상태이며, "세션"이라는 별도 도메인 실체를 만들지 않는다.
 *
 * :refresh, DELETE(로그아웃), PUT .../password 는 이 PR 범위가 아니다 (별도 PR).
 */
@Tag(name = "관리자 인증", description = "관리자 로그인/로그아웃/토큰 재발급")
@RestController
@RequestMapping("${admin.auth-path}")
class AdminAuthController {

    // auth.md: Set-Cookie: refreshToken=...; HttpOnly; Secure; SameSite=Strict; Path=...
    private static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";

    private final AdminAuthService adminAuthService;
    private final String refreshTokenCookiePath;
    private final boolean refreshCookieSecure;

    AdminAuthController(
            AdminAuthService adminAuthService,
            @Value("${admin.auth-path}") String adminAuthPath,
            @Value("${admin.refresh-cookie-secure}") boolean refreshCookieSecure) {
        this.adminAuthService = adminAuthService;
        this.refreshTokenCookiePath = parentPath(adminAuthPath);
        this.refreshCookieSecure = refreshCookieSecure;
    }

    @Operation(
            summary = "관리자 로그인",
            description = "아이디와 비밀번호로 인증해 관리자 토큰을 발급한다. 액세스 토큰은 응답 본문으로, "
                    + "리프레시 토큰은 HttpOnly 쿠키로 내려간다. 5회 실패 시 잠금 정책은 이번 범위에 포함하지 않는다."
    )
    @ApiResponse(responseCode = "201", description = "발급 성공")
    @ApiResponse(responseCode = "401", description = "아이디 또는 비밀번호 불일치. 사유를 구분해 알리지 않는다 (ADMIN-001)")
    @PostMapping
    ResponseEntity<ResponseEnvelope<AdminLoginResponse>> login(
            @Valid @RequestBody AdminLoginRequest request) {
        AdminLoginResult result = adminAuthService.login(request);

        /*
         * auth.md "관리자 > 재발급과 로그아웃" 절의 쿠키 설정을 따른다.
         * Path 는 로그인과 재발급이 함께 쓰는 /v1/admin/auth/ 범위로 제한한다.
         * Secure 는 설정으로 분리해 로컬 개발(HTTP)에서는 끌 수 있게 한다.
         */
        ResponseCookie refreshTokenCookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, result.refreshToken())
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite("Strict")
                .path(refreshTokenCookiePath)
                .maxAge(result.refreshTokenValiditySeconds())
                .build();

        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString())
                .body(ResponseEnvelope.success(result.response()));
    }

    // refreshToken 쿠키를 로그인/재발급 경로에서 함께 쓰기 위해 상위 경로를 구한다.
    private static String parentPath(String path) {
        int lastSlash = path.lastIndexOf('/');
        return lastSlash < 0 ? "/" : path.substring(0, lastSlash + 1);
    }
}