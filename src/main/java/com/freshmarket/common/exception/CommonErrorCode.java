package com.freshmarket.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/* 도메인에 속하지 않는 공통 오류다.
   도메인 고유 오류는 각 도메인의 domain.exception 패키지에 둔다 (DPB-2). */
@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode {

    INVALID_ARGUMENT(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT",
            "요청 값이 올바르지 않습니다. 상세 항목을 확인해 주세요."),

    INVALID_IDENTIFIER(HttpStatus.BAD_REQUEST, "INVALID_IDENTIFIER",
            "식별자 형식이 올바르지 않습니다."),

    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
            "로그인이 필요합니다."),

    /* 권한이 없으면 존재 여부와 무관하게 이것을 낸다 (API-7-05).
       리소스가 있는지 없는지를 응답으로 구분해 주면 그 자체가 정보 노출이다. */
    PERMISSION_DENIED(HttpStatus.FORBIDDEN, "PERMISSION_DENIED",
            "이 작업을 수행할 권한이 없습니다."),

    NOT_FOUND(HttpStatus.NOT_FOUND, "NOT_FOUND",
            "요청한 대상을 찾을 수 없습니다."),

    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
            "지원하지 않는 요청 방식입니다."),

    /* 재시도하면 성공할 수 있는 오류다 (API-7-06, CMP-4-03).
       유일 제약 위반이나 낙관적 잠금 충돌이 여기 해당한다. */
    CONFLICT(HttpStatus.CONFLICT, "CONFLICT",
            "이미 처리되었거나 다른 작업과 충돌했습니다. 잠시 후 다시 시도해 주세요."),

    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE",
            "요청 본문이 허용 크기를 넘었습니다."),

    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS",
            "요청이 너무 잦습니다. 잠시 후 다시 시도해 주세요."),

    INTERNAL(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL",
            "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요."),

    UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "UNAVAILABLE",
            "일시적으로 서비스를 이용할 수 없습니다. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus httpStatus;
    private final String reason;
    private final String message;
}
