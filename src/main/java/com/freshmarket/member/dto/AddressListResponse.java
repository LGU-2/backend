package com.freshmarket.member.dto;

import java.util.List;

// (2026-08-18 13:10) docs/api/member.md 응답 예시가 배송지 목록을 배열 그대로가 아니라
// {"addresses": [...]}로 감싸서 준다 — data가 리스트 자체가 아니라 리스트를 담은 객체다.
public record AddressListResponse(List<AddressResponse> addresses) {
}
