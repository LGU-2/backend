package com.freshmarket.common.exception;

import org.springframework.http.HttpStatus;

/* 도메인마다 자기 ErrorCode enum 을 두고 이 인터페이스를 구현한다.
   공통 코드는 CommonErrorCode 에 있다. */
public interface ErrorCode {

    /** 표준 HTTP 상태 코드 (CMP-4-01). */
    HttpStatus getHttpStatus();

    /* 기계 판독용 식별자다 (CMP-4-02).
       클라이언트의 분기 근거이므로 필드명과 같은 수준의 계약이다.
       한 번 내보낸 값의 의미를 바꾸지 않고, 새 상황이면 새 값을 추가한다. */
    String getReason();

    /* 사람이 읽는 기본 문구다.
       해결 방향을 담되 내부 구현과 민감 정보를 넣지 않는다 (API-7-04). */
    String getMessage();
}
