package com.freshmarket.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.freshmarket.common.exception.ErrorCode;
import java.util.List;

/* AIP-193 의 표준 오류 구조다 (API-7-03).
   최상위에 error 하나만 두고 그 안에 코드, 상태, 문구, 상세를 담는다.

   AIP-193 의 ErrorInfo 는 reason, domain, metadata 셋인데 metadata 를 두지 않는다.
   담을 값이 오류마다 달라 계약으로 굳지 않고, 필드별 상세는 로그에 남긴다. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(Error error) {

    public record Error(
            int code,
            String status,
            String message,
            List<Detail> details) {
    }

    /* reason 이 클라이언트의 분기 근거다 (CMP-4-02).
       메시지 문자열로 분기하면 문구를 다듬는 것만으로 장애가 난다. */
    public record Detail(
            String reason,
            String domain) {
    }

    public static ErrorResponse of(ErrorCode errorCode, String domain) {
        return of(errorCode, domain, errorCode.getMessage());
    }

    public static ErrorResponse of(ErrorCode errorCode, String domain, String message) {
        Detail detail = new Detail(errorCode.getReason(), domain);
        return new ErrorResponse(new Error(
                errorCode.getHttpStatus().value(),
                errorCode.getHttpStatus().name(),
                message,
                List.of(detail)));
    }
}
