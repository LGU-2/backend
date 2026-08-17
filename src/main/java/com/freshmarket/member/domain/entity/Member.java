package com.freshmarket.member.domain.entity;

import com.freshmarket.common.entity.BaseMutableTimeEntity;
import com.freshmarket.common.logging.PiiMasker;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;

/**
 * 컬럼명/제약은 V1__init_schema.sql의 member 테이블을 그대로 따른다 — 스키마는 Flyway가
 * 소유하고(ddl-auto: validate), 이 엔티티는 그 구조에 맞춰 매핑만 한다.
 *
 * 생성은 @Builder(access=PRIVATE) + 이름 있는 정적 팩토리(register())로만 열어둔다 — public
 * builder()를 그대로 노출하면 필수값(provider/providerUserId/memberGradeId) 누락을 컴파일
 * 타임에 못 막는다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "member")
@Check(name = "chk_member_status", constraints = "status IN ('PENDING_PROFILE','ACTIVE','BLOCKED','WITHDRAWN')")
@Check(name = "chk_member_refresh_token", constraints = "(refresh_token_hash IS NULL AND refresh_token_expires_at IS NULL) "
        + "OR (refresh_token_hash IS NOT NULL AND refresh_token_expires_at IS NOT NULL)")
@Check(name = "chk_member_withdrawn", constraints = "(status = 'WITHDRAWN' AND deleted_at IS NOT NULL) "
        + "OR (status <> 'WITHDRAWN' AND deleted_at IS NULL)")
public class Member extends BaseMutableTimeEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 30)
    private SocialType provider;

    @Column(name = "provider_user_id", nullable = false, length = 100)
    private String providerUserId;

    /**
     * "{provider}:{providerUserId}" 활성 식별 키 — deleted_at IS NULL을 기준으로 DB가 계산하는
     * GENERATED 컬럼(VIRTUAL, DDL에 STORED 명시 없음). 애플리케이션은 직접 쓰지 않는다.
     */
    @Column(name = "active_provider_key", insertable = false, updatable = false, unique = true, length = 140,
            columnDefinition = "VARCHAR(140) GENERATED ALWAYS AS "
                    + "(CASE WHEN deleted_at IS NULL THEN CONCAT(provider, ':', provider_user_id) ELSE NULL END)")
    private String activeProviderKey;

    // 카카오에서 받지 않고 온보딩 폼 입력값을 저장한다(completeOnboarding() 참고).
    @Column(length = 255)
    private String email;

    // DDL: VARCHAR(50). unique=true는 DDL엔 없는 애플리케이션 자체 제약 — existsByNickname
    // 선조회 방식의 동시성 레이스가 알려진 채 남아있다.
    @Column(unique = true, length = 50)
    private String nickname;

    // DDL의 member.name(폼 입력 실명) — 카카오 nickname과 별개 필드.
    @Column(length = 50)
    private String name;

    @Column(name = "member_grade_id", nullable = false)
    private Long memberGradeId;

    @Column(name = "terms_agreed_at")
    private LocalDateTime termsAgreedAt;

    @Column(name = "is_marketing_agreed", nullable = false)
    private boolean marketingAgreed;

    @Column(length = 20)
    private String phone;

    @Column(length = 255)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(30) COLLATE utf8mb4_0900_as_cs")
    private MemberStatus status;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // 로그인/토큰 재발급 인프라만 이 두 컬럼을 직접 건드린다.
    @Column(name = "refresh_token_hash", columnDefinition = "CHAR(64)")
    private String refreshTokenHash;

    @Column(name = "refresh_token_expires_at")
    private LocalDateTime refreshTokenExpiresAt;

    @Builder(access = AccessLevel.PRIVATE)
    private Member(SocialType provider, String providerUserId, MemberRole role, Long memberGradeId) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.providerUserId = Objects.requireNonNull(providerUserId, "providerUserId");
        this.role = (role != null) ? role : MemberRole.ROLE_USER;
        this.memberGradeId = Objects.requireNonNull(memberGradeId, "memberGradeId");
        this.status = MemberStatus.PENDING_PROFILE;
    }

    /** 카카오 최초 로그인 시 신규 회원 생성 — 유일한 생성 진입점. */
    public static Member register(SocialType provider, String providerUserId, Long memberGradeId) {
        return Member.builder()
                .provider(provider)
                .providerUserId(providerUserId)
                .role(MemberRole.ROLE_USER)
                .memberGradeId(memberGradeId)
                .build();
    }

    /** DB의 GENERATED 컬럼 계산식과 반드시 같은 규칙을 유지해야 한다(MemberRepository 조회 조건용). */
    public static String buildActiveProviderKey(SocialType provider, String providerUserId) {
        return provider.name() + ":" + providerUserId;
    }

    public Member assignNickname(String nickname) {
        this.nickname = nickname;
        return this;
    }

    public Member updateProfile(String name, String nickname, String email, String phone, String address) {
        this.name = name;
        assignNickname(nickname);
        this.email = email;
        if (phone != null) {
            this.phone = phone.isBlank() ? null : phone;
        }
        if (address != null) {
            this.address = address.isBlank() ? null : address;
        }
        return this;
    }

    public Member completeOnboarding(String name, String nickname, String email, LocalDateTime termsAgreedAt, boolean marketingAgreed) {
        this.name = name;
        assignNickname(nickname);
        this.email = email;
        this.termsAgreedAt = termsAgreedAt;
        this.marketingAgreed = marketingAgreed;
        if (this.status == MemberStatus.PENDING_PROFILE) {
            this.status = MemberStatus.ACTIVE;
        }
        return this;
    }

    public boolean isPendingProfile() {
        return this.status == MemberStatus.PENDING_PROFILE;
    }

    public boolean isWithdrawn() {
        return this.status == MemberStatus.WITHDRAWN;
    }

    /**
     * 탈퇴 처리(소프트 삭제). TODO(주문 도메인 추가 시): "진행 중 주문/미완료 환불이 있으면 탈퇴 불가"
     * 체크가 필요하다 — order 모듈이 없는 지금은 체크하지 않는다.
     */
    public void withdraw() {
        if (isWithdrawn()) {
            return;
        }
        this.status = MemberStatus.WITHDRAWN;
        this.deletedAt = LocalDateTime.now();
    }

    /** email/phone/address/providerUserId 등 민감정보가 새어나가지 않도록 방어적으로 오버라이드. */
    @Override
    public String toString() {
        return "Member{id=%s, nickname=%s, email=%s, status=%s, role=%s}"
                .formatted(getId(), nickname, PiiMasker.maskEmail(email), status, role);
    }
}
