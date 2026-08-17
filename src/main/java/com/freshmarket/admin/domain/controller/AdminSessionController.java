package com.freshmarket.admin.domain.controller;

import com.freshmarket.admin.domain.dto.AdminSessionCreateRequest;
import com.freshmarket.admin.domain.dto.AdminSessionResponse;
import com.freshmarket.admin.domain.service.AdminSessionService;
import com.freshmarket.common.response.ResponseEnvelope;
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
@RestController
@RequestMapping("/v1/admin/sessions")
@RequiredArgsConstructor
class AdminSessionController {

    private final AdminSessionService adminSessionService;

    @PostMapping
    ResponseEntity<ResponseEnvelope<AdminSessionResponse>> create(
            @Valid @RequestBody AdminSessionCreateRequest request) {
        AdminSessionResponse response = adminSessionService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ResponseEnvelope.success(response));
    }
}