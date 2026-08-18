package com.freshmarket.member.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.freshmarket.member.domain.MemberWithdrawalEvent;
import com.freshmarket.member.domain.entity.Member;
import com.freshmarket.member.domain.entity.SocialType;
import com.freshmarket.member.domain.oauth.KakaoIdTokenExchanger;
import com.freshmarket.member.domain.repository.MemberRepository;
import com.freshmarket.member.exception.AuthErrorCode;
import com.freshmarket.member.exception.AuthException;
import com.freshmarket.member.exception.MemberErrorCode;
import com.freshmarket.member.exception.MemberException;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.oauth2.jwt.Jwt;

// (2026-08-18 19:10) API 점검 중 발견한 커버리지 게이트 갭(0개)을 메운다. 탈퇴 전 카카오
// 재인증(본인 확인) 검증이 이 세션에서 새로 추가된 요구사항이라 그 분기를 중점적으로 본다.
@ExtendWith(MockitoExtension.class)
class MemberWithdrawalServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MemberTokenService memberTokenService;

    @Mock
    private KakaoIdTokenExchanger kakaoIdTokenExchanger;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private MemberWithdrawalService sut;

    @BeforeEach
    void setUp() {
        sut = new MemberWithdrawalService(memberRepository, memberTokenService, kakaoIdTokenExchanger, eventPublisher);
    }

    private static Member newMember(Long id) {
        Member member = Member.register(SocialType.KAKAO, "kakao-1", 1L);
        setId(member, id);
        return member;
    }

    private static void setId(Object entity, Long id) {
        try {
            Field field = entity.getClass().getSuperclass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Jwt idTokenWithSub(String sub) {
        return Jwt.withTokenValue("id-token")
                .header("alg", "none")
                .claim("sub", sub)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
    }

    @Test
    void 존재하지_않는_회원이면_예외() {
        when(memberRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.withdraw(1L, "이유", "code", "state"))
                .isInstanceOf(MemberException.class)
                .extracting(e -> ((MemberException) e).getErrorCode())
                .isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    void 이미_탈퇴한_회원이면_예외() {
        Member member = newMember(1L);
        member.withdraw();
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> sut.withdraw(1L, "이유", "code", "state"))
                .isInstanceOf(MemberException.class)
                .extracting(e -> ((MemberException) e).getErrorCode())
                .isEqualTo(MemberErrorCode.MEMBER_ALREADY_WITHDRAWN);
    }

    @Test
    void 재인증한_카카오_계정이_본인과_다르면_예외() {
        Member member = newMember(1L);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(kakaoIdTokenExchanger.exchange("code", "state")).thenReturn(idTokenWithSub("다른-kakao-id"));

        assertThatThrownBy(() -> sut.withdraw(1L, "이유", "code", "state"))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.REAUTH_ACCOUNT_MISMATCH);
        assertThat(member.isWithdrawn()).isFalse();
    }

    @Test
    void 재인증까지_통과하면_탈퇴_처리하고_토큰을_비우고_이벤트를_발행한다() {
        Member member = newMember(1L);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(kakaoIdTokenExchanger.exchange("code", "state")).thenReturn(idTokenWithSub("kakao-1"));

        sut.withdraw(1L, "이유", "code", "state");

        assertThat(member.isWithdrawn()).isTrue();
        verify(memberTokenService).revoke(1L, "ROLE_USER", false);
        verify(eventPublisher).publishEvent(new MemberWithdrawalEvent(1L, "kakao-1"));
    }

    @Test
    void 웹훅으로_들어오면_재인증_없이_바로_탈퇴_처리한다() {
        Member member = newMember(1L);
        when(memberRepository.findByActiveProviderKey("KAKAO:kakao-1")).thenReturn(Optional.of(member));

        sut.withdrawByKakaoWebhook("kakao-1");

        assertThat(member.isWithdrawn()).isTrue();
        verify(memberTokenService).revoke(1L, "ROLE_USER", false);
        verify(kakaoIdTokenExchanger, never()).exchange(anyString(), anyString());
    }

    @Test
    void 웹훅_대상_회원이_없어도_예외를_던지지_않는다() {
        when(memberRepository.findByActiveProviderKey("KAKAO:kakao-1")).thenReturn(Optional.empty());

        assertThatCode(() -> sut.withdrawByKakaoWebhook("kakao-1")).doesNotThrowAnyException();
        verify(memberTokenService, never()).revoke(any(), any(), anyBoolean());
    }
}
