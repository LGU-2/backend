package com.freshmarket.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "관리자 로그인 요청")
public record AdminSessionCreateRequest(

        @Schema(description = "관리자 로그인 아이디", example = "admin.kim")
        @NotBlank(message = "아이디를 입력해 주세요.")
        String loginId,

        @Schema(description = "비밀번호", example = "Passw0rd!2026")
        @NotBlank(message = "비밀번호를 입력해 주세요.")
        String password
) {
}