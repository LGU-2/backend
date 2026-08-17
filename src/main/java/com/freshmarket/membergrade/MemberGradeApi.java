package com.freshmarket.membergrade;

import java.util.Optional;

/**
 * membergrade 도메인이 다른 도메인에 공개하는 계약. 도메인 루트에 두는 이유는
 * domain-package-boundary-guideline.md의 원칙("도메인 루트에는 API와 DTO만") 때문이며,
 * 이 인터페이스가 없으면 다른 도메인이 membergrade.domain.repository를 직접 참조하게 되어
 * ArchUnit의 "도메인_내부는_다른_도메인에_닫혀_있다" 규칙을 어긴다.
 *
 * 회원가입 시 기본 등급을 찾아야 하는 member 도메인이 이 Api를 경유한다.
 */
public interface MemberGradeApi {

    /** 회원가입 시 배정할 기본 등급의 ID. 기본 등급 행이 없으면 empty. */
    Optional<Long> findDefaultGradeId();
}
