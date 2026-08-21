package com.freshmarket.admin.domain.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AdminRole {

    ADMIN("관리자"),
    SUPER_ADMIN("최고관리자");

    private final String displayName;

    /*
     * Spring Security 의 hasRole()/hasAuthority() 판정은 "ROLE_" 접두사가 붙은 문자열을 전제로
     * 동작한다 (hasRole 은 내부적으로 이 접두사를 자동으로 붙여 비교한다).
     * 접두사를 붙이는 지점을 name() 을 쓰는 각 호출부에 흩어 두면 한쪽에서 빠뜨려도 컴파일은 통과하고 인가만 조용히 실패한다.
     * 그래서 변환을 이 메서드 하나로 모은다.
     */
    public String toAuthority() { return "ROLE_" + name(); }
}