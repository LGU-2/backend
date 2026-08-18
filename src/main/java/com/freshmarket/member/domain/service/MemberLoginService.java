package com.freshmarket.member.domain.service;

import com.freshmarket.member.domain.entity.Member;
import com.freshmarket.member.domain.entity.SocialType;
import com.freshmarket.member.domain.oauth.KakaoAuthorizationService;
import com.freshmarket.member.domain.oauth.KakaoIdTokenExchanger;
import com.freshmarket.member.domain.oauth.OAuthAttributes;
import com.freshmarket.member.domain.repository.MemberGradeRepository;
import com.freshmarket.member.domain.repository.MemberRepository;
import com.freshmarket.member.exception.MemberErrorCode;
import com.freshmarket.member.exception.MemberException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// (2026-08-18 12:30) docs/api/auth.md의 "POST /v1/auth/tokens"(회원 로그인 완료) 전체를
// 오케스트레이션한다. com.example.freshdemo의 CustomOidcUserService가 하던 "검증된 사용자로
// Member 조회/생성"과, OAuth2LoginSuccessHandler가 하던 "토큰 발급"을 여기 하나로 합쳤다 —
// 예전엔 이 둘이 Spring Security 필터가 서로 다른 시점에 불러주는 별개의 콜백이었지만, 지금은
// 필터가 없어서 그냥 한 서비스 메서드 안의 순서 있는 두 단계가 됐다.
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberLoginService {

    private static final String NAME_ATTRIBUTE_KEY = "sub";

    private final KakaoAuthorizationService kakaoAuthorizationService;
    private final KakaoIdTokenExchanger kakaoIdTokenExchanger;
    private final MemberRepository memberRepository;
    private final MemberGradeRepository memberGradeRepository;
    private final MemberTokenService memberTokenService;

    public String authorizationUrl(boolean forceReauth) {
        return kakaoAuthorizationService.buildAuthorizationUrl(forceReauth);
    }

    @Transactional
    public LoginResult login(String authorizationCode, String state, boolean rememberMe, HttpServletResponse response) {
        Jwt idToken = kakaoIdTokenExchanger.exchange(authorizationCode, state);

        OAuthAttributes attrs = OAuthAttributes.of(SocialType.KAKAO, NAME_ATTRIBUTE_KEY, idToken.getClaims());

        Member member = findOrCreateMember(attrs);

        MemberTokenService.IssueResult issueResult = memberTokenService.issue(member, rememberMe, response);
        return new LoginResult(issueResult.accessToken(), issueResult.expiresInSeconds(), member);
    }

    private Member findOrCreateMember(OAuthAttributes attrs) {
        String activeProviderKey = Member.buildActiveProviderKey(attrs.provider(), attrs.providerUserId());
        return memberRepository.findByActiveProviderKey(activeProviderKey)
                .orElseGet(() -> registerNewMember(attrs, activeProviderKey));
    }

    private Member registerNewMember(OAuthAttributes attrs, String activeProviderKey) {
        try {
            Long defaultGradeId = memberGradeRepository.findByIsDefaultTrue()
                    .map(grade -> grade.getId())
                    .orElseThrow(() -> new MemberException(MemberErrorCode.DEFAULT_MEMBER_GRADE_NOT_FOUND));
            return memberRepository.saveAndFlush(attrs.toEntity(defaultGradeId));
        } catch (DataIntegrityViolationException e) {
            return memberRepository.findByActiveProviderKey(activeProviderKey)
                    .orElseThrow(() -> {
                        log.warn("event=MEMBER_LOGIN_FAILED reason=SIGNUP_RACE_UNRESOLVED provider={}", attrs.provider());
                        return e;
                    });
        }
    }

    public record LoginResult(String accessToken, long expiresInSeconds, Member member) {
    }
}
