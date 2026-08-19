package com.freshmarket.member.domain.service;

import com.freshmarket.common.auth.AuthCookieFactory;
import com.freshmarket.common.auth.jwt.AccessTokenValidAfterRepository;
import com.freshmarket.common.auth.jwt.JwtTokenProvider;
import com.freshmarket.common.auth.jwt.OpaqueTokenGenerator;
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
 * refreshTokenHash/refreshTokenExpiresAt에 DB 백업을 write-through로 남긴다.
 *
 * (2026-08-19) opaque 토큰 전환(SEC-1-04): 리프레시 토큰은 더 이상 JWT가 아니라
 * OpaqueTokenGenerator가 만든 무작위 문자열이다 — 클라이언트가 보낸 토큰만 봐서는 누구 건지
 * 전혀 알 수 없어서, reissue()가 "토큰에서 클레임을 먼저 읽고 조회"가 아니라 "Redis 조회부터
 * 하고 나서 알아내는" 순서로 뒤집혔다. 이 때문에 예전에 있던 "Redis 장애 시 DB CAS로 폴백"은
 * 더 이상 못 한다 — DB 백업(Member.refreshTokenHash)은 memberId로 찾는 컬럼인데, Redis가
 * 죽으면 애초에 이 토큰이 누구 건지 알 방법이 없어서 그 컬럼을 조회할 memberId 자체를 못 구한다.
 * 지금은 이 경우 REFRESH_TOKEN_INVALID로 재로그인을 요구한다 — Redis 가용성이 리프레시
 * 재발급의 하드 디펜던시가 됐다는 뜻이라, 별도로 팀 공유가 필요하다.
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
        String refreshToken = OpaqueTokenGenerator.generate();

        trySaveDbBackup(memberId, TokenHasher.sha256(refreshToken), LocalDateTime.now().plus(ttl));
        try {
            refreshTokenRepository.save(refreshToken, memberId, role, TokenType.MEMBER, rememberMe, ttl);
        } catch (DataAccessException e) {
            log.warn("event=REDIS_SAVE_FAILED role={} id={} — DB 백업만 반영됨", role, memberId, e);
        }

        response.addHeader(HttpHeaders.SET_COOKIE, authCookieFactory.refreshTokenCookie(refreshToken, rememberMe).toString());
        // (2026-08-18 16:20) accessToken도 다시 쿠키로 내려준다(요청에 따라 헤더 방식에서 되돌림).
        response.addHeader(HttpHeaders.SET_COOKIE, authCookieFactory.accessTokenCookie(accessToken).toString());

        return new IssueResult(accessToken, jwtTokenProvider.getAccessTokenValidityMs() / 1000);
    }

    /**
     * POST /v1/auth/tokens:refresh용. opaque 토큰이라 컨트롤러가 미리 검증/디코딩할 게 없다 —
     * 쿠키에서 꺼낸 문자열을 그대로 넘겨받아 여기서 Redis 조회부터 시작한다.
     */
    @Transactional
    public ReissueResult reissue(String oldRefreshToken) {
        String newRefreshToken = OpaqueTokenGenerator.generate();
        Duration ttl = Duration.ofMillis(jwtTokenProvider.getRefreshTokenValidityMs());
        LocalDateTime expiresAt = LocalDateTime.now().plus(ttl);

        RefreshTokenRepository.RefreshTokenData rotated;
        try {
            rotated = refreshTokenRepository.compareAndRotate(oldRefreshToken, newRefreshToken, ttl).orElse(null);
        } catch (DataAccessException e) {
            // Redis가 죽으면 이 토큰이 누구 건지 알 방법 자체가 없다 — DB CAS 폴백이 불가능한
            // 이유는 클래스 주석 참고. 재로그인을 요구하는 것 말고 할 수 있는 게 없다.
            log.warn("event=REDIS_CAS_FAILED — Redis 장애로 재발급 불가(재로그인 필요)", e);
            throw new AuthException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }

        if (rotated == null) {
            // (2026-08-19) opaque 전환 이후: 이 old 토큰이 누구 것이었는지 자체를 모르니, 예전처럼
            // "그 회원의 세션 전체를 강제로 끊는" 조치는 이제 이 자리에서 못 한다 — Redis에 이미
            // 없는 토큰이라 소유자 정보를 못 얻는다. 이 요청 자체를 거부하는 것으로 그친다.
            // (재사용 탐지의 "다시 쓰인 옛 토큰을 걸러낸다"는 목적 자체는 그대로 살아있다 — 못
            // 하게 된 건 "탐지 시 그 회원의 현재 유효 토큰까지 같이 죽이는" 부가 조치뿐이다.)
            log.warn("event=REFRESH_TOKEN_REUSE_SUSPECTED tokenHash={}", TokenHasher.sha256(oldRefreshToken));
            throw new AuthException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }

        Long memberId = rotated.memberId();
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.REFRESH_TOKEN_INVALID));

        if (member.isWithdrawn()) {
            revoke(memberId, rotated.role(), false);
            throw new AuthException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }

        String role = member.getRole().name();
        String newAccessToken = jwtTokenProvider.createAccessToken(memberId, TokenType.MEMBER, role);

        trySaveDbBackup(memberId, TokenHasher.sha256(newRefreshToken), expiresAt);
        return new ReissueResult(newAccessToken, jwtTokenProvider.getAccessTokenValidityMs() / 1000, newRefreshToken, rotated.remember());
    }

    /**
     * 로그아웃/탈퇴 시 토큰 폐기. logoutExternalSession=true면 카카오 세션도 끊는다(일반
     * /members/logout에서만 true — 탈퇴 흐름은 카카오 unlink를 MemberWithdrawalEvent로 별도
     * 처리하므로 여기서는 false로 호출한다). 카카오 로그아웃 자체는 MemberLogoutEvent로 커밋
     * 이후에 호출한다(KakaoLogoutEventListener) — MemberWithdrawalEvent/KakaoUnlinkEventListener와
     * 같은 이유(DI-4-02): @Transactional 안에서 동기로 부르면 카카오 응답 대기 동안 DB 커넥션이
     * 묶인다.
     *
     * refreshTokenRepository.delete(role, memberId)는 opaque 전환 이후에도 시그니처가 그대로다 —
     * 보조 인덱스(회원 → 현재 토큰)로 찾아서 지우므로, 호출부가 실제 토큰 문자열을 몰라도(탈퇴/
     * 웹훅 경로) 그대로 쓸 수 있다.
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
