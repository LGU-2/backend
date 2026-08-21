package com.freshmarket.product;

import java.util.List;

// 다른 도메인이 상품/옵션 존재 여부를 확인할 때 쓰는 공개 창구
public interface ProductApi {

    // productId 소속으로 optionId가 실제 존재하는지 확인한다
    boolean existsOption(Long productId, Long optionId);

    // productId 소속 옵션들의 ID 목록을 반환한다. 빈 리스트면 상품이 없다는 뜻이다
    List<Long> findOptionIds(Long productId);
}
