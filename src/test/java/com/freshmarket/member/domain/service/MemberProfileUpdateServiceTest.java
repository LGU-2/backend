package com.freshmarket.member.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.freshmarket.member.domain.entity.Member;
import com.freshmarket.member.domain.entity.MemberGrade;
import com.freshmarket.member.domain.entity.MemberStatus;
import com.freshmarket.member.domain.entity.SocialType;
import com.freshmarket.member.domain.repository.MemberGradeRepository;
import com.freshmarket.member.domain.repository.MemberRepository;
import com.freshmarket.member.dto.MemberProfileUpdateRequest;
import com.freshmarket.member.dto.MemberResponse;
import com.freshmarket.member.exception.MemberErrorCode;
import com.freshmarket.member.exception.MemberException;
import java.lang.reflect.Field;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// (2026-08-18 13:25) docs/api/member.md 기준 재작성 — 예전엔 "일반 수정"만 다뤘지만, 지금은
// GET 조회 + (온보딩 흡수한) 부분 수정 PATCH를 모두 이 서비스가 담당한다.
// (2026-08-18 15:10) 브랜치 전환 중 커밋 안 된 상태로 이 파일이 통째로 날아갔던 걸 복구함 —
// 내용 변경 없이 그대로 다시 썼다.
@ExtendWith(MockitoExtension.class)
class MemberProfileUpdateServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MemberGradeRepository memberGradeRepository;

    private MemberProfileUpdateService sut;

    @BeforeEach
    void setUp() {
        sut = new MemberProfileUpdateService(memberRepository, memberGradeRepository);
    }

    private static Member newMember() {
        return Member.register(SocialType.KAKAO, "kakao-1", 1L);
    }

    private static MemberGrade newGrade() {
        MemberGrade grade = MemberGrade.register("브론즈", null, true);
        setId(grade, 1L);
        return grade;
    }

    // BaseMutableTimeEntity.id는 @GeneratedValue라 테스트 더블에서 리플렉션으로 채운다.
    private static void setId(Object entity, Long id) {
        try {
            Field field = entity.getClass().getSuperclass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static MemberProfileUpdateRequest request(String name, String nickname, String email, String phone, Boolean marketingAgreed) {
        return new MemberProfileUpdateRequest(name, nickname, email, phone, marketingAgreed);
    }

    @Test
    void 존재하지_않는_회원이면_예외() {
        when(memberRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.updateProfile(1L, request("이름", "닉네임", "a@b.com", "010-1234-5678", null)))
                .isInstanceOf(MemberException.class)
                .extracting(e -> ((MemberException) e).getErrorCode())
                .isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND);
    }

    // (2026-08-18 19:10) getMyProfile()이 여태 한 번도 직접 호출되지 않아 100% 메서드 커버리지
    // 게이트를 못 넘기고 있었다 — API 점검 중 발견해 이 두 케이스를 추가했다.
    @Test
    void 내_정보를_조회한다() {
        Member member = newMember();
        member.updateProfile("이름", "닉네임", "a@b.com", "010-1234-5678", true);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(memberGradeRepository.findById(1L)).thenReturn(Optional.of(newGrade()));

        MemberResponse result = sut.getMyProfile(1L);

        assertThat(result.nickname()).isEqualTo("닉네임");
        assertThat(result.grade().name()).isEqualTo("브론즈");
    }

    @Test
    void 존재하지_않는_회원의_정보를_조회하면_예외() {
        when(memberRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.getMyProfile(1L))
                .isInstanceOf(MemberException.class)
                .extracting(e -> ((MemberException) e).getErrorCode())
                .isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND);
    }

    // (2026-08-18 20:05) findGrade()의 실패 분기(orElseThrow)를 어떤 테스트도 태우지 않아
    // JaCoCo가 그 람다를 별도 메서드로 잡고 methods covered ratio 0.83으로 게이트를 막았다.
    @Test
    void 회원의_등급을_찾지_못하면_예외() {
        Member member = newMember();
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(memberGradeRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.getMyProfile(1L))
                .isInstanceOf(MemberException.class)
                .extracting(e -> ((MemberException) e).getErrorCode())
                .isEqualTo(MemberErrorCode.DEFAULT_MEMBER_GRADE_NOT_FOUND);
    }

    @Test
    void 탈퇴한_회원이면_예외() {
        Member member = newMember();
        member.withdraw();
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> sut.updateProfile(1L, request("이름", "닉네임", "a@b.com", "010-1234-5678", null)))
                .isInstanceOf(MemberException.class)
                .extracting(e -> ((MemberException) e).getErrorCode())
                .isEqualTo(MemberErrorCode.MEMBER_ALREADY_WITHDRAWN);
    }

    @Test
    void 이미_다른_회원이_쓰는_닉네임이면_예외() {
        Member member = newMember();
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(memberRepository.existsByNickname("닉네임")).thenReturn(true);

        assertThatThrownBy(() -> sut.updateProfile(1L, request("이름", "닉네임", "a@b.com", "010-1234-5678", null)))
                .isInstanceOf(MemberException.class)
                .extracting(e -> ((MemberException) e).getErrorCode())
                .isEqualTo(MemberErrorCode.DUPLICATE_NICKNAME);
    }

    @Test
    void 보낸_필드만_반영되고_나머지는_그대로다() {
        Member member = newMember();
        member.updateProfile("기존이름", "기존닉네임", "old@b.com", "010-0000-0000", true);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(memberGradeRepository.findById(1L)).thenReturn(Optional.of(newGrade()));

        MemberResponse result = sut.updateProfile(1L, request(null, null, null, "010-9999-9999", null));

        assertThat(member.getName()).isEqualTo("기존이름");
        assertThat(member.getNickname()).isEqualTo("기존닉네임");
        assertThat(member.getEmail()).isEqualTo("old@b.com");
        assertThat(member.getPhone()).isEqualTo("010-9999-9999");
        assertThat(member.isMarketingAgreed()).isTrue();
        assertThat(result.grade().name()).isEqualTo("브론즈");
    }

    @Test
    void 닉네임이_기존과_같으면_중복검사를_하지_않는다() {
        Member member = newMember();
        member.assignNickname("닉네임");
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(memberGradeRepository.findById(1L)).thenReturn(Optional.of(newGrade()));

        sut.updateProfile(1L, request("이름", "닉네임", "a@b.com", "010-1234-5678", null));

        verify(memberRepository, never()).existsByNickname(any());
    }

    @Test
    void 필수항목이_다_채워지면_PENDING_PROFILE에서_ACTIVE로_전환된다() {
        Member member = newMember();
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(memberRepository.existsByNickname("닉네임")).thenReturn(false);
        when(memberGradeRepository.findById(1L)).thenReturn(Optional.of(newGrade()));

        MemberResponse result = sut.updateProfile(1L, request("이름", "닉네임", "a@b.com", null, null));

        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(result.status()).isEqualTo(MemberStatus.ACTIVE);
    }
}
