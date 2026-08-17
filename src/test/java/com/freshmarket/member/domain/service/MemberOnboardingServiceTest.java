package com.freshmarket.member.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.freshmarket.member.domain.entity.Member;
import com.freshmarket.member.domain.entity.SocialType;
import com.freshmarket.member.domain.repository.MemberRepository;
import com.freshmarket.member.exception.MemberErrorCode;
import com.freshmarket.member.exception.MemberException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemberOnboardingServiceTest {

    @Mock
    private MemberRepository memberRepository;

    private MemberOnboardingService sut;

    @BeforeEach
    void setUp() {
        sut = new MemberOnboardingService(memberRepository);
    }

    @Test
    void 존재하지_않는_회원이면_예외() {
        when(memberRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.completeOnboarding(1L, "이름", "a@b.com", "닉네임", false))
                .isInstanceOf(MemberException.class)
                .extracting(e -> ((MemberException) e).getErrorCode())
                .isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    void 탈퇴한_회원이면_예외() {
        Member member = Member.register(SocialType.KAKAO, "kakao-1", 1L);
        member.withdraw();
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> sut.completeOnboarding(1L, "이름", "a@b.com", "닉네임", false))
                .isInstanceOf(MemberException.class)
                .extracting(e -> ((MemberException) e).getErrorCode())
                .isEqualTo(MemberErrorCode.MEMBER_ALREADY_WITHDRAWN);
    }

    @Test
    void 이미_다른_회원이_쓰는_닉네임이면_예외() {
        Member member = Member.register(SocialType.KAKAO, "kakao-1", 1L);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(memberRepository.existsByNickname("닉네임")).thenReturn(true);

        assertThatThrownBy(() -> sut.completeOnboarding(1L, "이름", "a@b.com", "닉네임", false))
                .isInstanceOf(MemberException.class)
                .extracting(e -> ((MemberException) e).getErrorCode())
                .isEqualTo(MemberErrorCode.DUPLICATE_NICKNAME);
    }

    @Test
    void 온보딩을_완료하면_ACTIVE로_바뀌고_입력값이_반영된다() {
        Member member = Member.register(SocialType.KAKAO, "kakao-1", 1L);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(memberRepository.existsByNickname("닉네임")).thenReturn(false);

        Member result = sut.completeOnboarding(1L, "이름", "a@b.com", "닉네임", true);

        assertThat(result.isPendingProfile()).isFalse();
        assertThat(result.getName()).isEqualTo("이름");
        assertThat(result.getNickname()).isEqualTo("닉네임");
        assertThat(result.getEmail()).isEqualTo("a@b.com");
        assertThat(result.isMarketingAgreed()).isTrue();
    }

    @Test
    void 닉네임이_기존과_같으면_중복검사를_하지_않는다() {
        Member member = Member.register(SocialType.KAKAO, "kakao-1", 1L);
        member.assignNickname("닉네임");
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        sut.completeOnboarding(1L, "이름", "a@b.com", "닉네임", false);

        verify(memberRepository).findById(1L);
        verifyNoMoreInteractions(memberRepository);
    }
}
