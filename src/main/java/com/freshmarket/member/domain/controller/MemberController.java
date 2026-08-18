package com.freshmarket.member.domain.controller;

import com.freshmarket.common.auth.CustomUserDetails;
import com.freshmarket.common.response.ResponseEnvelope;
import com.freshmarket.member.domain.service.MemberProfileUpdateService;
import com.freshmarket.member.dto.MemberProfileUpdateRequest;
import com.freshmarket.member.dto.MemberResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// (2026-08-18 11:15) com.example.freshdemo.member.domain.controller에서 이식.
// (2026-08-18 13:25) docs/api/member.md 기준 전면 재작성: 경로를 /v1/members로 옮기고, 문서에
// 없던 PATCH /me/onboarding을 없앴다(온보딩 완료 로직은 MemberProfileUpdateService.updateProfile()
// 로 흡수됨). 문서에만 있고 코드엔 없던 GET /v1/members/me(내 정보 조회)를 새로 추가했다.
/** 회원 프로필 API. */
@RestController
@RequestMapping("/v1/members")
@RequiredArgsConstructor
class MemberController {

    private final MemberProfileUpdateService memberProfileUpdateService;

    @GetMapping("/me")
    public ResponseEntity<ResponseEnvelope<MemberResponse>> getMyProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(ResponseEnvelope.success(memberProfileUpdateService.getMyProfile(userDetails.getId())));
    }

    @PatchMapping("/me")
    public ResponseEntity<ResponseEnvelope<MemberResponse>> updateProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody @Valid MemberProfileUpdateRequest request
    ) {
        return ResponseEntity.ok(ResponseEnvelope.success(memberProfileUpdateService.updateProfile(userDetails.getId(), request)));
    }
}
