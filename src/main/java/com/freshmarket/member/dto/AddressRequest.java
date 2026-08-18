package com.freshmarket.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// toEntity()를 두지 않는다 — Address.register() 정적 팩토리가 memberId 등 여러 인자를 받아
// AddressService가 조립하는 편이 자연스럽다.
// 길이 제약은 docs/api/member.md의 배송지 필드 표를 그대로 따른다 — DB 컬럼 길이와도 맞춰야
// 초과 입력이 DB 에러가 아니라 400으로 먼저 걸린다(Address 엔티티 @Column length 참고).
public record AddressRequest(
        @NotBlank @Size(max = 50) String recipient,
        @NotBlank @Size(max = 20) String phone,
        @NotBlank @Size(max = 10) String zipcode,
        @NotBlank @Size(max = 255) String roadAddress,
        @Size(max = 255) String detailAddress,
        boolean isDefault
) {
}
