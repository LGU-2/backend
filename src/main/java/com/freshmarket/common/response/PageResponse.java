package com.freshmarket.common.response;

import java.util.List;

/* AIP-158 의 목록 응답이다 (API-5-01).
   컬렉션은 처음부터 페이지네이션을 넣는다. 나중에 추가하면 호환성이 깨진다. */
public record PageResponse<T>(List<T> items, String nextPageToken) {

    public PageResponse {
        items = List.copyOf(items);
    }

    /** 마지막 페이지. nextPageToken 이 비어 있다는 것이 그 신호다. */
    public static <T> PageResponse<T> last(List<T> items) {
        return new PageResponse<>(items, null);
    }

    /* nextPageToken 은 클라이언트가 해석할 수 없는 불투명 문자열이어야 한다 (API-5-02).
       정렬 키나 오프셋을 그대로 노출하면 클라이언트가 그것에 의존하기 시작한다. */
    public static <T> PageResponse<T> of(List<T> items, String nextPageToken) {
        return new PageResponse<>(items, nextPageToken);
    }
}
