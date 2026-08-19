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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.freshmarket.common.auth.AuthCookieFactory;
import com.freshmarket.common.auth.jwt.AccessTokenValidAfterRepository;
import com.freshmarket.common.auth.jwt.JwtTokenProvider;
import com.freshmarket.common.auth.jwt.RefreshTokenRepository;
import com.freshmarket.common.auth.jwt.TokenType;
import com.freshmarket.member.domain.MemberLogoutEvent;
import com.freshmarket.member.domain.entity.Member;
import com.freshmarket.member.domain.entity.SocialType;
import com.freshmarket.member.domain.repository.MemberRepository;
import com.freshmarket.member.exception.AuthErrorCode;
import com.freshmarket.member.exception.AuthException;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;

@ExtendWith(MockitoExtension.class)
class MemberTokenServiceTest {

    private static final String TEST_JWT_SECRET = "test-jwt-secret-key-must-be-at-least-32-bytes-long";

    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private AccessTokenValidAfterRepository accessTokenValidAfterRepository;

    private AuthCookieFactory authCookieFactory;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private HttpServletResponse response;

    private MemberTokenService sut;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(TEST_JWT_SECRET, 1_800_000L, 1_209_600_000L);
        authCookieFactory = new AuthCookieFactory(jwtTokenProvider);

        sut = new MemberTokenService(jwtTokenProvider, refreshTokenRepository, accessTokenValidAfterRepository,
                authCookieFactory, memberRepository, eventPublisher);
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

        MemberTokenService.IssueResult result = sut.issue(member, true, response);

        // 실제 JwtTokenProvider가 서명한 진짜 토큰인지, 클레임이 맞는지 검증
        assertThat(jwtTokenProvider.validateToken(result.accessToken())).isTrue();
        assertThat(jwtTokenProvider.getId(result.accessToken())).isEqualTo(1L);
        assertThat(jwtTokenProvider.getType(result.accessToken())).isEqualTo(TokenType.MEMBER);
        assertThat(result.expiresInSeconds()).isEqualTo(1800L);

        // 응답에 실제로 실린 Set-Cookie 헤더를 캡처해서 AuthCookieFactory가 만든 속성을 직접 확인
        ArgumentCaptor<String> cookieCaptor = ArgumentCaptor.forClass(String.class);
        verify(response, times(2)).addHeader(eq(HttpHeaders.SET_COOKIE), cookieCaptor.capture());
        List<String> cookies = cookieCaptor.getAllValues();

        assertThat(cookies).anySatisfy(c -> {
            assertThat(c).startsWith("refreshToken=");
            assertThat(c).contains("Path=/v1/auth/");
            assertThat(c).contains("HttpOnly");
            assertThat(c).contains("SameSite=Strict");
        });
        assertThat(cookies).anySatisfy(c -> {
            assertThat(c).startsWith("accessToken=");
            assertThat(c).contains("Path=/");
            assertThat(c).contains("HttpOnly");
        });
    }

    @Test
    void redis_저장이_실패해도_발급_자체는_끝난다() {
        Member member = newMember(1L);
        doThrow(new DataAccessResourceFailureException("redis down"))
                .when(refreshTokenRepository).save(any(), any(), any(), any());

        assertThatCode(() -> sut.issue(member, false, response)).doesNotThrowAnyException();
    }

    @Test
    void db_백업_저장이_실패해도_발급_자체는_끝난다() {
        Member member = newMember(1L);
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
        when(refreshTokenRepository.compareAndSave(eq("ROLE_USER"), eq(1L), eq("old-rt"), anyString(), any()))
                .thenReturn(true);

        MemberTokenService.ReissueResult result = sut.reissue(1L, "ROLE_USER", "old-rt", false);

        assertThat(jwtTokenProvider.validateToken(result.accessToken())).isTrue();
        assertThat(jwtTokenProvider.getId(result.accessToken())).isEqualTo(1L);
        assertThat(jwtTokenProvider.validateToken(result.refreshToken())).isTrue();
        assertThat(jwtTokenProvider.getType(result.refreshToken())).isEqualTo(TokenType.MEMBER);
        assertThat(result.expiresInSeconds()).isEqualTo(1800L);
    }

    @Test
    void CAS가_실패하면_재사용_의심으로_토큰을_비우고_예외() {
        Member member = newMember(1L);
        // revoke() 실패 로그가 이 값을 getJti()로 파싱하므로, 실제 서명된 토큰이어야 한다
        // ("old-rt" 같은 임의 문자열은 JwtException을 던져 테스트가 의도와 다르게 깨진다).
        String oldRefreshToken = jwtTokenProvider.createRefreshToken(1L, TokenType.MEMBER, "ROLE_USER", false);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(refreshTokenRepository.compareAndSave(any(), any(), any(), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> sut.reissue(1L, "ROLE_USER", oldRefreshToken, false))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.REFRESH_TOKEN_INVALID);
        verify(memberRepository).clearRefreshToken(1L);
    }

    @Test
    void redis_CAS가_장애나면_DB_CAS로_폴백해서_성공할_수_있다() {
        Member member = newMember(1L);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(refreshTokenRepository.compareAndSave(any(), any(), any(), any(), any()))
                .thenThrow(new DataAccessResourceFailureException("redis down"));
        when(memberRepository.compareAndSetRefreshToken(eq(1L), anyString(), anyString(), any(LocalDateTime.class)))
                .thenReturn(1);

        MemberTokenService.ReissueResult result = sut.reissue(1L, "ROLE_USER", "old-rt", false);

        assertThat(jwtTokenProvider.validateToken(result.refreshToken())).isTrue();
        assertThat(jwtTokenProvider.getId(result.refreshToken())).isEqualTo(1L);
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
    void 외부세션_로그아웃_플래그가_true면_카카오_로그아웃_이벤트를_발행한다() {
        Member member = newMember(1L);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        sut.revoke(1L, "ROLE_USER", true);

        // 카카오 호출 자체는 @Transactional 밖(KakaoLogoutEventListener, AFTER_COMMIT)에서 일어난다 —
        // 여기서는 이벤트가 올바른 값으로 발행됐는지만 확인한다.
        verify(eventPublisher).publishEvent(new MemberLogoutEvent(1L, "kakao-1"));
    }

    @Test
    void 외부세션_로그아웃_플래그가_false면_카카오_로그아웃_이벤트를_발행하지_않는다() {
        sut.revoke(1L, "ROLE_USER", false);

        verify(eventPublisher, never()).publishEvent(any());
        verify(memberRepository, never()).findById(anyLong());
    }
}
