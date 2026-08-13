package com.freshmarket.common.exception;

import lombok.Getter;

/* 도메인 예외의 뿌리다. 각 도메인은 이것을 상속해 자기 예외를 만든다.
   abstract 이고 생성자가 protected 라 이 타입 자체로는 던질 수 없다.
   전역 핸들러가 ErrorCode 를 읽어 응답으로 옮기므로 컨트롤러에서 잡지 않는다. */
@Getter
public abstract class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /* transient 를 붙이지 않는다. 구현이 enum 이라 직렬화 가능하고,
       붙이면 역직렬화 뒤 null 이 되어 전역 핸들러가 코드를 잃는다. */
    private final ErrorCode errorCode;

    protected BusinessException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    protected BusinessException(ErrorCode errorCode, Throwable cause) {
        /* 상위 생성자에 넘기는 문구는 로그용이다.
           응답에 나가는 문구는 전역 핸들러가 ErrorCode 에서 다시 꺼낸다. */
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }
}
