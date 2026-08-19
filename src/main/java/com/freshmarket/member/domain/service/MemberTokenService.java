package com.freshmarket.member.domain.service;

import com.freshmarket.common.auth.AuthCookieFactory;
import com.freshmarket.common.auth.jwt.AccessTokenValidAfterRepository;
import com.freshmarket.common.auth.jwt.JwtTokenProvider;
import com.freshmarket.common.auth.jwt.RefreshTokenRepository;
import com.freshmarket.common.auth.jwt.TokenHasher;
import com.freshmarket.common.auth.jwt.TokenType;
import com.freshmarket.member.domain.MemberLogoutEvent;
import com.freshmarket.member.domain.entity.Member;
import com.freshmarket.member.domain.repository.MemberRepository;
import com.freshmarket.member.exception.AuthErrorCode;
import com.freshmarket.member.exception.AuthException;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// issue()/reissue() 둘 다 accessToken(+수명)을 반환값에 담아 컨트롤러가 응답 본문/쿠키를
// 조립하는 데 쓸 수 있게 한다. 재발급 실패는 BadCredentialsException(스프링 시큐리티 제네릭
// 타입) 대신 AuthException(AUTH-004)으로 던져 문서가 정한 에러코드가 그대로 응답에 실린다.
/**
 * 회원 로그인/재발급/로그아웃 시 토큰(access/refresh) 발급·회전·폐기를 담당. common.auth.jwt의
 * RefreshTokenRepository(순수 Redis)를 1차 저장소로 쓰고, Member 행의
 * refreshTokenHash/refreshTokenExpiresAt에 DB 백업을 write-through로 남긴다 — Redis 장애 시
 * (특히 reissue의 compareAndSave) DB CAS로 폴백한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberTokenService {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AccessTokenValidAfterRepository accessTokenValidAfterRepository;
    private final AuthCookieFactory authCookieFactory;
    private final MemberRepository memberRepository;
    private final ApplicationEventPublisher eventPublisher;

    public record IssueResult(String accessToken, long expiresInSeconds) {
    }

    public record ReissueResult(String accessToken, long expiresInSeconds, String refreshToken, boolean remember) {
    }

    /** 카카오 로그인 성공 시 토큰 발급. accessToken/refreshToken 둘 다 쿠키로 나가고, accessToken은
     * 호출부(컨트롤러)가 만료 시각 등 안내용으로 쓸 수 있게 반환값에도 담는다. */
    @Transactional
    public IssueResult issue(Member member, boolean rememberMe, HttpServletResponse response) {
        Long memberId = member.getId();
        String role = member.getRole().name();
        Duration ttl = Duration.ofMillis(jwtTokenProvider.getRefreshTokenValidityMs());

        String accessToken = jwtTokenProvider.createAccessToken(memberId, TokenType.MEMBER, role);
        String refreshToken = jwtTokenProvider.createRefreshToken(memberId, TokenType.MEMBER, role, rememberMe);

        trySaveDbBackup(memberId, TokenHasher.sha256(refreshToken), LocalDateTime.now().plus(ttl));
        try {
            refreshTokenRepository.save(role, memberId, refreshToken, ttl);
        } catch (DataAccessException e) {
            log.warn("event=REDIS_SAVE_FAILED role={} id={} — DB 백업만 반영됨", role, memberId, e);
        }

        response.addHeader(HttpHeaders.SET_COOKIE, authCookieFactory.refreshTokenCookie(refreshToken, rememberMe).toString());
        // (2026-08-18 16:20) accessToken도 다시 쿠키로 내려준다(요청에 따라 헤더 방식에서 되돌림).
        response.addHeader(HttpHeaders.SET_COOKIE, authCookieFactory.accessTokenCookie(accessToken).toString());

        return new IssueResult(accessToken, jwtTokenProvider.getAccessTokenValidityMs() / 1000);
    }

    /**
     * POST /v1/auth/tokens:refresh용. 리프레시 토큰 서명·클레임 검증은 컨트롤러가 먼저 끝내고 넘겨준다.
     */
    @Transactional
    public ReissueResult reissue(Long memberId, String claimedRole, String oldRefreshToken, boolean remember) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.REFRESH_TOKEN_INVALID));

        if (member.isWithdrawn()) {
            revoke(memberId, claimedRole, false);
            throw new AuthException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }

        String role = member.getRole().name();
        String newAccessToken = jwtTokenProvider.createAccessToken(memberId, TokenType.MEMBER, role);
        String newRefreshToken = jwtTokenProvider.createRefreshToken(memberId, TokenType.MEMBER, role, remember);
        Duration ttl = Duration.ofMillis(jwtTokenProvider.getRefreshTokenValidityMs());
        LocalDateTime expiresAt = LocalDateTime.now().plus(ttl);

        boolean rotated;
        try {
            rotated = refreshTokenRepository.compareAndSave(claimedRole, memberId, oldRefreshToken, newRefreshToken, ttl);
        } catch (DataAccessException e) {
            log.warn("event=REDIS_CAS_FAILED role={} id={} — DB CAS로 폴백", claimedRole, memberId, e);
            String oldHash = TokenHasher.sha256(oldRefreshToken);
            String newHash = TokenHasher.sha256(newRefreshToken);
            rotated = memberRepository.compareAndSetRefreshToken(memberId, oldHash, newHash, expiresAt) > 0;
        }

        if (!rotated) {
            revoke(memberId, claimedRole, false);
            log.warn("event=REFRESH_TOKEN_REUSE_SUSPECTED role={} id={} jti={}",
                    claimedRole, memberId, jwtTokenProvider.getJti(oldRefreshToken));
            throw new AuthException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }

        trySaveDbBackup(memberId, TokenHasher.sha256(newRefreshToken), expiresAt);
        return new ReissueResult(newAccessToken, jwtTokenProvider.getAccessTokenValidityMs() / 1000, newRefreshToken, remember);
    }

    /**
     * 로그아웃/탈퇴 시 토큰 폐기. logoutExternalSession=true면 카카오 세션도 끊는다(일반
     * /members/logout에서만 true — 탈퇴 흐름은 카카오 unlink를 MemberWithdrawalEvent로 별도
     * 처리하므로 여기서는 false로 호출한다). 카카오 로그아웃 자체는 MemberLogoutEvent로 커밋
     * 이후에 호출한다(KakaoLogoutEventListener) — MemberWithdrawalEvent/KakaoUnlinkEventListener와
     * 같은 이유(DI-4-02): @Transactional 안에서 동기로 부르면 카카오 응답 대기 동안 DB 커넥션이
     * 묶인다.
     */
    @Transactional
    public void revoke(Long memberId, String role, boolean logoutExternalSession) {
        try {
            memberRepository.clearRefreshToken(memberId);
        } catch (DataAccessException e) {
            log.warn("event=DB_BACKUP_DELETE_FAILED memberId={} — DB 백업 삭제 실패(계속 진행)", memberId, e);
        }
        try {
            refreshTokenRepository.delete(role, memberId);
        } catch (DataAccessException e) {
            log.warn("event=REDIS_DELETE_FAILED role={} id={} — DB 백업만 반영됨", role, memberId, e);
        }
        accessTokenValidAfterRepository.invalidateBefore(role, memberId, LocalDateTime.now(),
                Duration.ofMillis(jwtTokenProvider.getAccessTokenValidityMs()));

        if (logoutExternalSession) {
            memberRepository.findById(memberId)
                    .map(Member::getProviderUserId)
                    .ifPresent(providerUserId -> eventPublisher.publishEvent(new MemberLogoutEvent(memberId, providerUserId)));
        }
    }

    private void trySaveDbBackup(Long memberId, String tokenHash, LocalDateTime expiresAt) {
        try {
            int updated = memberRepository.updateRefreshToken(memberId, tokenHash, expiresAt);
            if (updated == 0) {
                log.warn("event=DB_BACKUP_SAVE_SKIPPED memberId={} — 대상 행을 찾지 못함", memberId);
            }
        } catch (DataAccessException e) {
            log.warn("event=DB_BACKUP_SAVE_FAILED memberId={} — Redis만 반영됨(DB 백업 유실 가능, 다음 쓰기 때 다시 시도됨)",
                    memberId, e);
        }
    }
}
