package com.freshmarket.member.domain.controller;

import com.freshmarket.common.auth.CustomUserDetails;
import com.freshmarket.common.response.ResponseEnvelope;
import com.freshmarket.member.domain.entity.Address;
import com.freshmarket.member.domain.service.AddressService;
import com.freshmarket.member.dto.AddressListResponse;
import com.freshmarket.member.dto.AddressRequest;
import com.freshmarket.member.dto.AddressResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// (2026-08-18 11:50) com.example.freshdemo.address.domain.controller에서 이식 — address가
// member 도메인 하위로 재구성되면서(domain-map.md 기준) member.domain.controller로 옮겨왔다.
// (2026-08-18 13:10) docs/api/member.md 기준으로 경로를 /v1/members/me/addresses로 옮기고,
// 수정을 PUT에서 PATCH로 바꿨다(문서 표가 PATCH로 명시). 목록 응답은 배열이 아니라
// {"addresses": [...]}로 감싸서 준다(AddressListResponse 참고).
/** 회원 배송지 API. */
@RestController
@RequestMapping("/v1/members/me/addresses")
@RequiredArgsConstructor
class AddressController {

    private final AddressService addressService;

    @GetMapping
    public ResponseEntity<ResponseEnvelope<AddressListResponse>> findMyAddresses(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        var responses = addressService.findMyAddresses(userDetails.getId()).stream()
                .map(AddressResponse::from)
                .toList();
        return ResponseEntity.ok(ResponseEnvelope.success(new AddressListResponse(responses)));
    }

    @PostMapping
    public ResponseEntity<ResponseEnvelope<AddressResponse>> create(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody @Valid AddressRequest request
    ) {
        Address address = addressService.create(userDetails.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ResponseEnvelope.success(AddressResponse.from(address)));
    }

    @PatchMapping("/{addressId}")
    public ResponseEntity<ResponseEnvelope<AddressResponse>> update(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long addressId,
            @RequestBody @Valid AddressRequest request
    ) {
        Address address = addressService.update(userDetails.getId(), addressId, request);
        return ResponseEntity.ok(ResponseEnvelope.success(AddressResponse.from(address)));
    }

    @DeleteMapping("/{addressId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long addressId
    ) {
        addressService.delete(userDetails.getId(), addressId);
        return ResponseEntity.noContent().build();
    }
}
