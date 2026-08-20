package com.freshmarket.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

// (2026-08-18 13:25) docs/api/member.md 필드 표 그대로: 부분 수정이라 전부 선택 필드다("보낸
// 필드만 바뀐다"). marketingAgreed는 false와 "안 보냄"을 구분해야 해서 boolean이 아니라
// Boolean(null 허용)으로 뒀다. address는 문서 표에 없어 뺐다(배송지는 별도 Address API).
// (2026-08-18 15:10) 브랜치 전환 중 커밋 안 된 상태로 이 파일이 통째로 날아갔던 걸 복구함 —
// 내용 변경 없이 그대로 다시 썼다.
public record MemberProfileUpdateRequest(
        @Size(max = 50) String name,
        @Size(max = 50) String nickname,
        @Email @Size(max = 255) String email,
        @Size(max = 20) String phone,
        Boolean marketingAgreed
) {
}
