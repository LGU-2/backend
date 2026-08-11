package com.freshmarket.common.response;

/* 성공 응답 봉투다.
   AIP 는 리소스를 그대로 돌려주고 봉투를 씌우지 않으므로 이 클래스는 그 관례에서 벗어난다.
   판단 근거는 api-design-rationale.md 를 참고하고, 쓰지 않기로 하면 이 클래스를 지운다. */
public record ApiResponse<T>(T data) {

    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data);
    }
}
