package com.freshmarket.member.domain;

import com.freshmarket.member.domain.client.KakaoUnlinkClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// (2026-08-18 11:45) com.example.freshdemo.member.domain에서 이식, 로직 무변경.
/** 이벤트 리스너 어댑터라 서비스가 아니고, domain.service 패키지(커버리지 100% 대상)에 있으면 안 된다. */
@Component
@RequiredArgsConstructor
public class KakaoUnlinkEventListener {

    private final KakaoUnlinkClient kakaoUnlinkClient;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(MemberWithdrawalEvent event) {
        kakaoUnlinkClient.unlink(event.kakaoUserId());
    }
}
