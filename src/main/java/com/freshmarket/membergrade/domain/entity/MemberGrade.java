package com.freshmarket.membergrade.domain.entity;

import com.freshmarket.common.entity.BaseMutableTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원 등급(단골 구분용). member_grade 테이블(V1__init_schema.sql)에 대응한다.
 *
 * member.memberGradeId가 이 표를 NOT NULL FK로 참조한다 — 신규 회원 생성 시 isDefault=true인
 * 행을 자동으로 찾아 배정한다. isDefault=true 행 "최대 1개"는 isDefaultKey 생성 컬럼 + UNIQUE로
 * DB가 강제한다. "최소 1개"는 DB가 못 막는 조건이라 DefaultMemberGradeInitializer(기동 시 확인
 * 후 없으면 시드)가 담당한다.
 *
 * 생성은 @Builder(access=PRIVATE) + 이름 있는 정적 팩토리(register())로만 열어둔다 — public
 * builder()를 그대로 노출하면 필수값(name) 누락을 컴파일 타임에 못 막는다.
 */
@Entity
@Getter
@Table(name = "member_grade")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberGrade extends BaseMutableTimeEntity {

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Column(name = "promotion_rule", length = 255)
    private String promotionRule;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "is_default_key", insertable = false, updatable = false, unique = true,
            columnDefinition = "TINYINT GENERATED ALWAYS AS (CASE WHEN is_default THEN 1 ELSE NULL END)")
    private Integer isDefaultKey;

    @Builder(access = AccessLevel.PRIVATE)
    private MemberGrade(String name, String promotionRule, boolean isDefault) {
        this.name = Objects.requireNonNull(name, "name");
        this.promotionRule = promotionRule;
        this.isDefault = isDefault;
    }

    /** 등급 정의 — 유일한 생성 진입점. */
    public static MemberGrade register(String name, String promotionRule, boolean isDefault) {
        return MemberGrade.builder()
                .name(name)
                .promotionRule(promotionRule)
                .isDefault(isDefault)
                .build();
    }

    @Override
    public String toString() {
        return "MemberGrade{id=%s, name=%s, isDefault=%s}".formatted(getId(), name, isDefault);
    }
}
