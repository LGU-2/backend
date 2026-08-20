package com.freshmarket.stock.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

// 로트 입고 등록 요청. receivedDate를 생략하면 서비스가 오늘 날짜로 채운다
public record AdminLotCreateRequest(
        @Schema(description = "입고일. 생략하면 오늘", example = "2026-08-17") LocalDate receivedDate,
        @Schema(description = "소비기한", example = "2026-08-31") @NotNull LocalDate expiryDate,
        @Schema(description = "입고 수량", example = "200") @NotNull @Min(1) Integer initialQty
) {
}
