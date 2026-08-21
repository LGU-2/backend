package com.freshmarket.stock.domain.service;

import com.freshmarket.product.ProductApi;
import com.freshmarket.stock.domain.dto.ExpiringSoonResponse;
import com.freshmarket.stock.domain.repository.StockLotRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 회원에게 소비기한 임박 상품을 노출한다
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExpiringSoonService {

    private final StockLotRepository stockLotRepository;
    private final ProductApi productApi;

    /*
     * TODO: 판정 기준 확정 대기 중 (이슈 본문 참고).
     * product.md 는 "쿠폰 캠페인 대상 선정과 같은 기준"을 요구하는데,
     * coupon.md 에는 그 기준이 아직 미정으로 남아있어 쿠폰팀 확인 후 구현한다.
     */
    public List<ExpiringSoonResponse> getExpiringSoonProducts(int withinDays, Long categoryId) {
        throw new UnsupportedOperationException("판정 기준 확정 대기 중 — 이슈 참고");
    }
}