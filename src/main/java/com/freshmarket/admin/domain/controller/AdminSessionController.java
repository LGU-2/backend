package com.freshmarket.admin.domain.controller;

import com.freshmarket.admin.domain.dto.AdminSessionCreateRequest;
import com.freshmarket.admin.domain.dto.AdminSessionResponse;
import com.freshmarket.admin.domain.service.AdminSessionService;
import com.freshmarket.common.response.ResponseEnvelope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/*
 * 로그인을 "세션 생성" 으로 모델링한다 (AIP-133). 커스텀 동사(:login) 대신 표준 Create 로
 * 표현할 수 있어서다 (API-3-01). 로그아웃(다음 PR)은 이 자원의 Delete 가 된다.
 */
@Tag(name = "관리자 세션", description = "관리자 로그인/로그아웃")
@RestController
@RequestMapping("/v1/admin/sessions")
@RequiredArgsConstructor
class AdminSessionController {

    private final AdminSessionService adminSessionService;

    @Operation(
            summary = "관리자 로그인",
            description = "아이디와 비밀번호로 인증해 관리자 세션(액세스 토큰, 리프레시 토큰)을 발급한다. "
                    + "5회 실패 시 잠금 정책은 이번 범위에 포함하지 않는다."
    )
    @ApiResponse(responseCode = "201", description = "로그인 성공, 토큰 발급")
    @ApiResponse(responseCode = "401", description = "아이디 또는 비밀번호가 올바르지 않음 (사유 미노출)")
    @ApiResponse(responseCode = "403", description = "비활성화된 계정")
    @PostMapping
    ResponseEntity<ResponseEnvelope<AdminSessionResponse>> create(
            @Valid @RequestBody AdminSessionCreateRequest request) {
        AdminSessionResponse response = adminSessionService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ResponseEnvelope.success(response));
    }
}