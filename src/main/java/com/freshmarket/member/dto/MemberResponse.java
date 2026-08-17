package com.freshmarket.member.dto;

import com.freshmarket.member.domain.entity.Member;
import com.freshmarket.member.domain.entity.MemberRole;
import com.freshmarket.member.domain.entity.MemberStatus;
import java.time.LocalDateTime;

public record MemberResponse(
        String name,
        String nickname,
        String email,
        String phone,
        String address,
        boolean marketingAgreed,
        MemberRole role,
        MemberStatus status,
        LocalDateTime createdAt
) {
    public static MemberResponse from(Member member) {
        return new MemberResponse(
                member.getName(),
                member.getNickname(),
                member.getEmail(),
                member.getPhone(),
                member.getAddress(),
                member.isMarketingAgreed(),
                member.getRole(),
                member.getStatus(),
                member.getCreatedAt()
        );
    }
}
