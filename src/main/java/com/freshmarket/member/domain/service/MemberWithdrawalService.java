package com.freshmarket.member.domain.service;

import com.freshmarket.member.domain.MemberWithdrawalEvent;
import com.freshmarket.member.domain.entity.Member;
import com.freshmarket.member.domain.entity.SocialType;
import com.freshmarket.member.domain.oauth.KakaoIdTokenExchanger;
import com.freshmarket.member.domain.repository.MemberRepository;
import com.freshmarket.member.exception.AuthErrorCode;
import com.freshmarket.member.exception.AuthException;
import com.freshmarket.member.exception.MemberErrorCode;
import com.freshmarket.member.exception.MemberException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// docs/api/member.md 기준 "탈퇴 전 카카오 재인증" 요구사항을 검증 단계로 구현한다.
/**
 * 회원탈퇴 유스케이스. 순서: 0) 카카오 재인증(id_token) 검증 — 본인 계정인지 확인
 * 1) DB 상태 변경(WITHDRAWN) 2) refreshToken 삭제 3) accessTokenValidAfter 커트라인 등록
 * 4) 카카오 unlink는 AFTER_COMMIT 이벤트로 미룸(KakaoUnlinkEventListener).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberWithdrawalService {

    private static final String SUBJECT_CLAIM = "sub";

    private final MemberRepository memberRepository;
    private final MemberTokenService memberTokenService;
    private final KakaoIdTokenExchanger kakaoIdTokenExchanger;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void withdraw(Long memberId, String reason, String authorizationCode, String state) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));

        if (member.isWithdrawn()) {
            throw new MemberException(MemberErrorCode.MEMBER_ALREADY_WITHDRAWN);
        }

        // TODO(주문 도메인 추가 시): 진행 중 주문/미완료 환불이 있으면 여기서 막아야 한다
        // (MEMBER-001/MEMBER-002).

        verifyReauth(member, authorizationCode, state);

        // reason을 담을 컬럼이 스키마에 없다(V1__init_schema.sql 기준) — 우선 로그로만 남긴다.
        // 감사(audit) 목적으로 영구 보관이 필요해지면 별도 이력 테이블/마이그레이션이 필요하다.
        log.info("event=MEMBER_WITHDRAWN memberId={} reason={}", memberId, reason);

        String kakaoUserId = member.getProviderUserId();

        member.withdraw();

        memberTokenService.revoke(memberId, member.getRole().name(), false);

        eventPublisher.publishEvent(new MemberWithdrawalEvent(memberId, kakaoUserId));
    }

    // GET /v1/auth/kakao/authorize?reauth=true 로 받은 code/state를 로그인 때와 같은 방식으로
    // 검증한다(state/nonce/서명/발급자 전부 재사용). 재인증 자체는 성공했더라도 다른 카카오
    // 계정으로 재로그인했다면 본인 확인이 안 된 것이므로 AUTH-005로 별도 구분한다.
    private void verifyReauth(Member member, String authorizationCode, String state) {
        Jwt idToken = kakaoIdTokenExchanger.exchange(authorizationCode, state);
        String reauthenticatedProviderUserId = idToken.getClaimAsString(SUBJECT_CLAIM);

        if (!member.getProviderUserId().equals(reauthenticatedProviderUserId)) {
            throw new AuthException(AuthErrorCode.REAUTH_ACCOUNT_MISMATCH);
        }
    }

    /** 카카오 쪽에서 먼저 연결을 끊은 경우(웹훅으로 통보) — DB 상태만 맞추고 unlink는 다시 호출하지 않는다. */
    @Transactional
    public void withdrawByKakaoWebhook(String kakaoUserId) {
        String activeProviderKey = Member.buildActiveProviderKey(SocialType.KAKAO, kakaoUserId);

        memberRepository.findByActiveProviderKey(activeProviderKey)
                .ifPresent(member -> {
                    member.withdraw();
                    memberTokenService.revoke(member.getId(), member.getRole().name(), false);
                });
        // 회원이 없거나 이미 탈퇴 상태여도 예외를 던지지 않는다 — 웹훅 응답은 무조건 200이어야 한다.
    }
}
