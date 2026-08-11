package com.freshmarket.common.exception;

import java.util.Map;
import lombok.Getter;

/* 도메인 예외의 뿌리다. 각 도메인은 이것을 상속해 자기 예외를 만든다.
   전역 핸들러가 ErrorCode 를 읽어 응답으로 옮기므로 컨트롤러에서 잡지 않는다. */
@Getter
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /* transient 를 붙이지 않는다. 구현이 enum 이라 직렬화 가능하고,
       붙이면 역직렬화 뒤 null 이 되어 전역 핸들러가 코드를 잃는다. */
    private final ErrorCode errorCode;

    /* 오류마다 달라지는 동적 정보다 (API-7-03 의 metadata).
       식별자나 상태값처럼 클라이언트가 판단에 쓸 값만 넣고 내부 구조는 넣지 않는다. */
    private final Map<String, String> metadata;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, Map.of(), null);
    }

    public BusinessException(ErrorCode errorCode, Map<String, String> metadata) {
        this(errorCode, metadata, null);
    }

    public BusinessException(ErrorCode errorCode, Map<String, String> metadata, Throwable cause) {
        /* 상위 생성자에 넘기는 문구는 로그용이다.
           응답에 나가는 문구는 전역 핸들러가 ErrorCode 에서 다시 꺼낸다. */
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
        this.metadata = Map.copyOf(metadata);
    }

    /** 값 하나만 담는 흔한 경우의 진입점. */
    public static BusinessException of(ErrorCode errorCode, String key, String value) {
        return new BusinessException(errorCode, Map.of(key, value));
    }
}
