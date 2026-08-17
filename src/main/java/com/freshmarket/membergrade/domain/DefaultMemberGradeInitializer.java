package com.freshmarket.membergrade.domain;

import com.freshmarket.membergrade.domain.entity.MemberGrade;
import com.freshmarket.membergrade.domain.repository.MemberGradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * member.member_grade_id가 NOT NULL FK라, 회원가입이 되려면 member_grade에 isDefault=true인
 * 행이 최소 1개는 있어야 한다 — 없으면 가입 자체가 막힌다. Flyway 마이그레이션은 테이블
 * 구조만 정의하고 시드 데이터는 넣지 않으므로, 기동 시점에 확인해서 없으면 만들어준다.
 *
 * "최대 1개"는 member_grade.is_default_key(생성 컬럼) + UNIQUE로 DB가 강제하지만, "최소 1개
 * 존재"는 DB 제약만으로 표현할 수 없는 조건이라 이 초기화기가 그 역할을 진다.
 *
 * domain.service가 아니라 domain 바로 아래에 둔 이유: 기동 시 시드하는 초기화기라 요청 단위로
 * 호출되는 서비스가 아니고, domain.service 패키지는 100% 커버리지 게이트 대상이라 성격이
 * 다른 이 클래스를 거기 두면 게이트 취지에도 안 맞는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultMemberGradeInitializer implements ApplicationRunner {

    private static final String DEFAULT_GRADE_NAME = "일반";

    private final MemberGradeRepository memberGradeRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (memberGradeRepository.findByIsDefaultTrue().isPresent()) {
            return;
        }

        if (memberGradeRepository.existsByName(DEFAULT_GRADE_NAME)) {
            log.warn("event=DEFAULT_MEMBER_GRADE_SEED_SKIPPED reason=NAME_EXISTS_BUT_NOT_DEFAULT name={}", DEFAULT_GRADE_NAME);
            return;
        }

        memberGradeRepository.save(MemberGrade.register(DEFAULT_GRADE_NAME, null, true));
        log.info("event=DEFAULT_MEMBER_GRADE_SEEDED name={}", DEFAULT_GRADE_NAME);
    }
}
