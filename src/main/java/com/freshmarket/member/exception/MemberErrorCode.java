package com.freshmarket.member.exception;

import com.freshmarket.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * member 도메인 에러코드. DEFAULT_MEMBER_GRADE_NOT_FOUND는 원인은 membergrade 데이터지만
 * 실제 발생 지점(회원가입 흐름)을 기준으로 여기 뒀다 — membergrade로 옮기는 게 맞다고 판단되면 재배치.
 */
@Getter
@RequiredArgsConstructor
public enum MemberErrorCode implements ErrorCode {

    MEMBER_NOT_FOUND(HttpStatus.BAD_REQUEST, "MEMBER-001", "회원을 찾을 수 없습니다."),
    MEMBER_ALREADY_WITHDRAWN(HttpStatus.BAD_REQUEST, "MEMBER-002", "이미 탈퇴한 회원입니다."),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "MEMBER-003", "이미 사용 중인 닉네임입니다."),
    KAKAO_UNLINK_FAILED(HttpStatus.BAD_GATEWAY, "MEMBER-004", "카카오 연결 해제 요청에 실패했습니다."),
    DEFAULT_MEMBER_GRADE_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "MEMBER-005", "기본 회원 등급이 설정되어 있지 않습니다."),
    ;

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
