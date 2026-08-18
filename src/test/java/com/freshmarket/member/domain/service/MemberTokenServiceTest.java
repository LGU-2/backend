package com.freshmarket.member.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.freshmarket.common.auth.AuthCookieFactory;
import com.freshmarket.common.auth.jwt.AccessTokenValidAfterRepository;
import com.freshmarket.common.auth.jwt.JwtTokenProvider;
import com.freshmarket.common.auth.jwt.RefreshTokenRepository;
import com.freshmarket.common.auth.jwt.TokenType;
import com.freshmarket.member.domain.client.KakaoLogoutClient;
import com.freshmarket.member.domain.entity.Member;
import com.freshmarket.member.domain.entity.SocialType;
import com.freshmarket.member.domain.repository.MemberRepository;
import com.freshmarket.member.exception.AuthErrorCode;
import com.freshmarket.member.exception.AuthException;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

// (2026-08-18 19:10) API 점검 중 발견한 커버리지 게이트 갭(0개)을 메운다. 이 클래스가 이번
// 세션 내내 다룬 쿠키 방식 전환·CAS 회전·부분 장애 시 계속 진행하는 로직의 핵심이라 꼼꼼히 본다.
@ExtendWith(MockitoExtension.class)
class MemberTokenServiceTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private AccessTokenValidAfterRepository accessTokenValidAfterRepository;

    @Mock
    private AuthCookieFactory authCookieFactory;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private KakaoLogoutClient kakaoLogoutClient;

    @Mock
    private HttpServletResponse response;

    private MemberTokenService sut;

    @BeforeEach
    void setUp() {
        sut = new MemberTokenService(jwtTokenProvider, refreshTokenRepository, accessTokenValidAfterRepository,
                authCookieFactory, memberRepository, kakaoLogoutClient);
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

    // ---- issue() ----

    @Test
    void 발급하면_accessToken과_refreshToken_쿠키가_둘_다_실린다() {
        Member member = newMember(1L);
        when(jwtTokenProvider.createAccessToken(1L, TokenType.MEMBER, "ROLE_USER")).thenReturn("at");
        when(jwtTokenProvider.createRefreshToken(1L, TokenType.MEMBER, "ROLE_USER", true)).thenReturn("rt");
        when(jwtTokenProvider.getRefreshTokenValidityMs()).thenReturn(1_209_600_000L);
        when(jwtTokenProvider.getAccessTokenValidityMs()).thenReturn(1_800_000L);
        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", "rt").build();
        ResponseCookie accessCookie = ResponseCookie.from("accessToken", "at").build();
        when(authCookieFactory.refreshTokenCookie("rt", true)).thenReturn(refreshCookie);
        when(authCookieFactory.accessTokenCookie("at")).thenReturn(accessCookie);

        MemberTokenService.IssueResult result = sut.issue(member, true, response);

        assertThat(result.accessToken()).isEqualTo("at");
        assertThat(result.expiresInSeconds()).isEqualTo(1800L);
        verify(response).addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
        verify(response).addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
    }

    @Test
    void redis_저장이_실패해도_발급_자체는_끝난다() {
        Member member = newMember(1L);
        when(jwtTokenProvider.createAccessToken(any(), any(), any())).thenReturn("at");
        when(jwtTokenProvider.createRefreshToken(any(), any(), any(), eq(false))).thenReturn("rt");
        when(jwtTokenProvider.getRefreshTokenValidityMs()).thenReturn(1_209_600_000L);
        when(jwtTokenProvider.getAccessTokenValidityMs()).thenReturn(1_800_000L);
        when(authCookieFactory.refreshTokenCookie(anyString(), eq(false)))
                .thenReturn(ResponseCookie.from("refreshToken", "rt").build());
        when(authCookieFactory.accessTokenCookie(anyString()))
                .thenReturn(ResponseCookie.from("accessToken", "at").build());
        doThrow(new DataAccessResourceFailureException("redis down"))
                .when(refreshTokenRepository).save(any(), any(), any(), any());

        assertThatCode(() -> sut.issue(member, false, response)).doesNotThrowAnyException();
    }

    @Test
    void db_백업_저장이_실패해도_발급_자체는_끝난다() {
        Member member = newMember(1L);
        when(jwtTokenProvider.createAccessToken(any(), any(), any())).thenReturn("at");
        when(jwtTokenProvider.createRefreshToken(any(), any(), any(), eq(false))).thenReturn("rt");
        when(jwtTokenProvider.getRefreshTokenValidityMs()).thenReturn(1_209_600_000L);
        when(jwtTokenProvider.getAccessTokenValidityMs()).thenReturn(1_800_000L);
        when(authCookieFactory.refreshTokenCookie(anyString(), eq(false)))
                .thenReturn(ResponseCookie.from("refreshToken", "rt").build());
        when(authCookieFactory.accessTokenCookie(anyString()))
                .thenReturn(ResponseCookie.from("accessToken", "at").build());
        when(memberRepository.updateRefreshToken(any(), any(), any()))
                .thenThrow(new DataAccessResourceFailureException("db down"));

        assertThatCode(() -> sut.issue(member, false, response)).doesNotThrowAnyException();
    }

    // ---- reissue() ----

    @Test
    void 존재하지_않는_회원의_리프레시면_예외() {
        when(memberRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.reissue(1L, "ROLE_USER", "old-rt", false))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.REFRESH_TOKEN_INVALID);
    }

    @Test
    void 탈퇴한_회원의_리프레시면_토큰을_비우고_예외() {
        Member withdrawn = newMember(1L);
        withdrawn.withdraw();
        when(memberRepository.findById(1L)).thenReturn(Optional.of(withdrawn));

        assertThatThrownBy(() -> sut.reissue(1L, "ROLE_USER", "old-rt", false))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.REFRESH_TOKEN_INVALID);
        verify(memberRepository).clearRefreshToken(1L);
        verify(refreshTokenRepository).delete("ROLE_USER", 1L);
    }

    @Test
    void CAS가_성공하면_새_토큰을_돌려준다() {
        Member member = newMember(1L);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(jwtTokenProvider.createAccessToken(1L, TokenType.MEMBER, "ROLE_USER")).thenReturn("new-at");
        when(jwtTokenProvider.createRefreshToken(1L, TokenType.MEMBER, "ROLE_USER", false)).thenReturn("new-rt");
        when(jwtTokenProvider.getRefreshTokenValidityMs()).thenReturn(1_209_600_000L);
        when(jwtTokenProvider.getAccessTokenValidityMs()).thenReturn(1_800_000L);
        when(refreshTokenRepository.compareAndSave(eq("ROLE_USER"), eq(1L), eq("old-rt"), eq("new-rt"), any()))
                .thenReturn(true);

        MemberTokenService.ReissueResult result = sut.reissue(1L, "ROLE_USER", "old-rt", false);

        assertThat(result.accessToken()).isEqualTo("new-at");
        assertThat(result.refreshToken()).isEqualTo("new-rt");
        assertThat(result.expiresInSeconds()).isEqualTo(1800L);
    }

    @Test
    void CAS가_실패하면_재사용_의심으로_토큰을_비우고_예외() {
        Member member = newMember(1L);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(jwtTokenProvider.createAccessToken(any(), any(), any())).thenReturn("new-at");
        when(jwtTokenProvider.createRefreshToken(any(), any(), any(), eq(false))).thenReturn("new-rt");
        when(jwtTokenProvider.getRefreshTokenValidityMs()).thenReturn(1_209_600_000L);
        when(refreshTokenRepository.compareAndSave(any(), any(), any(), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> sut.reissue(1L, "ROLE_USER", "old-rt", false))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.REFRESH_TOKEN_INVALID);
        verify(memberRepository).clearRefreshToken(1L);
    }

    @Test
    void redis_CAS가_장애나면_DB_CAS로_폴백해서_성공할_수_있다() {
        Member member = newMember(1L);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(jwtTokenProvider.createAccessToken(any(), any(), any())).thenReturn("new-at");
        when(jwtTokenProvider.createRefreshToken(any(), any(), any(), eq(false))).thenReturn("new-rt");
        when(jwtTokenProvider.getRefreshTokenValidityMs()).thenReturn(1_209_600_000L);
        when(jwtTokenProvider.getAccessTokenValidityMs()).thenReturn(1_800_000L);
        when(refreshTokenRepository.compareAndSave(any(), any(), any(), any(), any()))
                .thenThrow(new DataAccessResourceFailureException("redis down"));
        when(memberRepository.compareAndSetRefreshToken(eq(1L), anyString(), anyString(), any(LocalDateTime.class)))
                .thenReturn(1);

        MemberTokenService.ReissueResult result = sut.reissue(1L, "ROLE_USER", "old-rt", false);

        assertThat(result.refreshToken()).isEqualTo("new-rt");
    }

    // ---- revoke() ----

    @Test
    void 로그아웃하면_저장소_세_곳을_모두_정리한다() {
        sut.revoke(1L, "ROLE_USER", false);

        verify(memberRepository).clearRefreshToken(1L);
        verify(refreshTokenRepository).delete("ROLE_USER", 1L);
        verify(accessTokenValidAfterRepository).invalidateBefore(eq("ROLE_USER"), eq(1L), any(), any());
    }

    @Test
    void db_삭제가_실패해도_나머지_정리는_계속된다() {
        doThrow(new DataAccessResourceFailureException("db down")).when(memberRepository).clearRefreshToken(1L);

        sut.revoke(1L, "ROLE_USER", false);

        verify(refreshTokenRepository).delete("ROLE_USER", 1L);
        verify(accessTokenValidAfterRepository).invalidateBefore(eq("ROLE_USER"), eq(1L), any(), any());
    }

    @Test
    void redis_삭제가_실패해도_나머지_정리는_계속된다() {
        doThrow(new DataAccessResourceFailureException("redis down")).when(refreshTokenRepository).delete("ROLE_USER", 1L);

        sut.revoke(1L, "ROLE_USER", false);

        verify(accessTokenValidAfterRepository).invalidateBefore(eq("ROLE_USER"), eq(1L), any(), any());
    }

    @Test
    void 외부세션_로그아웃_플래그가_true면_카카오_로그아웃을_호출한다() {
        Member member = newMember(1L);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        sut.revoke(1L, "ROLE_USER", true);

        verify(kakaoLogoutClient).logout("kakao-1");
    }

    @Test
    void 외부세션_로그아웃_플래그가_false면_카카오_로그아웃을_호출하지_않는다() {
        sut.revoke(1L, "ROLE_USER", false);

        verify(kakaoLogoutClient, never()).logout(anyString());
        verify(memberRepository, never()).findById(anyLong());
    }
}
