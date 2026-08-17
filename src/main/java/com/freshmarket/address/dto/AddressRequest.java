package com.freshmarket.address.dto;

import jakarta.validation.constraints.NotBlank;

// toEntity()를 두지 않는다 — Address.register() 정적 팩토리가 memberId 등 여러 인자를 받아
// AddressService가 조립하는 편이 자연스럽다.
public record AddressRequest(
        @NotBlank String recipient,
        @NotBlank String phone,
        @NotBlank String zipcode,
        @NotBlank String roadAddress,
        String detailAddress,
        boolean isDefault
) {
}
