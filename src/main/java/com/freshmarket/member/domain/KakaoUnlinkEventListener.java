package com.freshmarket.member.domain;

import com.freshmarket.common.logging.PiiMasker;
import com.freshmarket.member.domain.client.KakaoUnlinkClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 이벤트 리스너 어댑터라 서비스가 아니고, domain.service 패키지(커버리지 100% 대상)에 있으면 안 된다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoUnlinkEventListener {

    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_BACKOFF_MS = 300;

    private final KakaoUnlinkClient kakaoUnlinkClient;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(MemberWithdrawalEvent event) {

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                kakaoUnlinkClient.unlink(event.kakaoUserId());
                return;
            } catch (Exception e) {
                boolean lastAttempt = attempt == MAX_ATTEMPTS;
                if (lastAttempt) {
                    log.error("event=KAKAO_UNLINK_GAVE_UP memberId={} kakaoUserId={} attempts={}",
                            event.memberId(), PiiMasker.maskProviderId(event.kakaoUserId()), attempt, e);

                } else {
                    log.warn("event=KAKAO_UNLINK_RETRY memberId={} kakaoUserId={} attempt={}",
                            event.memberId(), PiiMasker.maskProviderId(event.kakaoUserId()), attempt, e);
                    // 탈퇴 시 카카오 연동 해제가 늦어져도 사용자의 불편감이 없음.
                    // Thread.sleep으로 재시도하지 않고 바로 아웃박스 패턴으로 넘긴다.

                    // TODO(아웃박스 패턴)
                    // 여기서 완전히 포기하면 DB는 탈퇴 상태, 카카오는 연결 유지상태로 영구히 어긋날 수 있다.
                    // kakao_unlink_task 같은 아웃박스 테이블에 실패 기록을 남기고
                    // (withdraw() 트랜잭션 안에서 같이 기록해야 이벤트 유실에도 안전),
                    // 별도 @Scheduled가 주기적으로 미완료 레코드를 재시도하는 구조로 보강한다.
                }
            }
        }
    }
}
