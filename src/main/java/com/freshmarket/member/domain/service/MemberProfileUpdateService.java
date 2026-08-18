package com.freshmarket.member.domain.service;

import com.freshmarket.member.domain.entity.Member;
import com.freshmarket.member.domain.entity.MemberGrade;
import com.freshmarket.member.domain.repository.MemberGradeRepository;
import com.freshmarket.member.domain.repository.MemberRepository;
import com.freshmarket.member.dto.MemberProfileUpdateRequest;
import com.freshmarket.member.dto.MemberResponse;
import com.freshmarket.member.exception.MemberErrorCode;
import com.freshmarket.member.exception.MemberException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// (2026-08-18 13:25) docs/api/member.md 기준 GET/PATCH /v1/members/me를 여기서 함께 담당한다.
// 예전엔 "온보딩 완료"(PATCH /members/me/onboarding, 전체 필드 필수 + 약관동의 체크)와
// "일반 수정"(PATCH /members/me, 전체 필드 필수)이 분리돼 있었는데, 문서엔 PATCH /v1/members/me
// 하나뿐이고 부분 수정이다 — 두 흐름을 하나로 합쳤다(사용자 확인, Member.updateProfile() 주석 참고).
// (2026-08-18 15:10) 브랜치 전환 중 커밋 안 된 상태로 이 파일이 통째로 날아갔던 걸 복구함 —
// 내용 변경 없이 그대로 다시 썼다.
@Service
@RequiredArgsConstructor
public class MemberProfileUpdateService {

    private final MemberRepository memberRepository;
    private final MemberGradeRepository memberGradeRepository;

    @Transactional(readOnly = true)
    public MemberResponse getMyProfile(Long memberId) {
        Member member = findActiveMember(memberId);
        return MemberResponse.from(member, findGrade(member.getMemberGradeId()));
    }

    @Transactional
    public MemberResponse updateProfile(Long memberId, MemberProfileUpdateRequest request) {
        Member member = findActiveMember(memberId);

        if (request.nickname() != null
                && !request.nickname().equals(member.getNickname())
                && memberRepository.existsByNickname(request.nickname())) {
            throw new MemberException(MemberErrorCode.DUPLICATE_NICKNAME);
        }

        member.updateProfile(request.name(), request.nickname(), request.email(), request.phone(), request.marketingAgreed());

        return MemberResponse.from(member, findGrade(member.getMemberGradeId()));
    }

    private Member findActiveMember(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));

        if (member.isWithdrawn()) {
            throw new MemberException(MemberErrorCode.MEMBER_ALREADY_WITHDRAWN);
        }

        return member;
    }

    private MemberGrade findGrade(Long memberGradeId) {
        return memberGradeRepository.findById(memberGradeId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.DEFAULT_MEMBER_GRADE_NOT_FOUND));
    }
}
