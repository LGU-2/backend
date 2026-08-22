package com.freshmarket.order.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 장바구니 기반 주문 생성 요청. requestId는 HTTP 재시도와 중복 클릭을 같은 주문으로 수렴시킨다. */
public record OrderCreateRequest(
        @NotBlank @Size(max = 64) String requestId,
        @NotEmpty @Size(max = 99) List<@NotNull @Positive Long> cartItemIds,
        @NotNull @Positive Long addressId,
        @Size(max = 255) String shipMessage
) {
}
