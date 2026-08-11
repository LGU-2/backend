package com.freshmarket.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.freshmarket.common.exception.ErrorCode;
import java.util.List;
import java.util.Map;

/* AIP-193 의 표준 오류 구조다 (API-7-03).
   최상위에 error 하나만 두고 그 안에 코드, 상태, 문구, 상세를 담는다. */
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
            String domain,
            Map<String, String> metadata) {
    }

    public static ErrorResponse of(ErrorCode errorCode, String domain, Map<String, String> metadata) {
        return of(errorCode, domain, metadata, errorCode.getMessage());
    }

    public static ErrorResponse of(ErrorCode errorCode, String domain,
                                   Map<String, String> metadata, String message) {
        Detail detail = new Detail(errorCode.getReason(), domain, metadata);
        return new ErrorResponse(new Error(
                errorCode.getHttpStatus().value(),
                errorCode.getHttpStatus().name(),
                message,
                List.of(detail)));
    }
}
