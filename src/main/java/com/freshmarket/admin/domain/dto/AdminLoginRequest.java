package com.freshmarket.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "관리자 로그인 요청")
public record AdminLoginRequest(

        @Schema(description = "관리자 로그인 아이디", example = "admin.kim")
        @NotBlank(message = "아이디를 입력해 주세요.")
        @Size(max = 50, message = "아이디는 50자를 넘을 수 없습니다.")   // admin.login_id 컬럼 길이(VARCHAR(50))와 맞춘다
        String loginId,

        @Schema(description = "비밀번호", example = "Passw0rd!2026")
        @NotBlank(message = "비밀번호를 입력해 주세요.")
        @Size(max = 72, message = "비밀번호는 72자를 넘을 수 없습니다.")   // BCrypt 는 72바이트를 넘는 입력을 조용히 잘라낸다 (SEC-3-03)
        String password
) {
}