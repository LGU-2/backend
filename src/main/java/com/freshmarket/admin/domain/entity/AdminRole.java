package com.freshmarket.admin.domain.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AdminRole {

    ADMIN("관리자"),
    SUPER_ADMIN("최고관리자");

    private final String displayName;

    // Spring Security 권한 문자열 포맷("ROLE_ADMIN", "ROLE_SUPER_ADMIN")으로 변환한다.
    public String toAuthority() {
        return "ROLE_" + name();
    }
}