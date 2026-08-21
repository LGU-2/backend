package com.freshmarket.payment.domain.service;

import com.freshmarket.payment.PaymentRequest;
import com.freshmarket.payment.PaymentResult;
import com.freshmarket.payment.domain.client.PaymentGatewayApproval;
import com.freshmarket.payment.domain.entity.Payment;
import com.freshmarket.payment.domain.exception.PaymentErrorCode;
import com.freshmarket.payment.domain.exception.PaymentException;
import com.freshmarket.payment.domain.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private final PaymentRepository paymentRepository;

    /*
     * TODO: PAYMENT_PENDING 주문이 결제 제한 시간을 넘으면 order를 CANCELED로, 각 order_item을
     * CANCELED로 바꾸고 StockApi.release(...)로 예약 재고를 복원하는 배치 스케줄러를 구현한다.
     * coupon 연동 뒤에는 이 경로에서 쿠폰 사용 상태도 함께 되돌린다.
     */

    // PG 호출 전에 PENDING 행을 별도 트랜잭션으로 확정한다. 외부 호출 동안 DB 트랜잭션을 잡지 않는다.
    @Transactional
    public Payment preparePayment(PaymentRequest request) {
        return paymentRepository.findByOrderId(request.orderId())
                .orElseGet(() -> paymentRepository.save(
                        Payment.prepare(request.orderId(), request.method(), request.amount())));
    }

    @Transactional
    public PaymentResult approvePayment(Long paymentId, PaymentGatewayApproval approval) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentException(PaymentErrorCode.PAYMENT_NOT_FOUND));

        payment.approve(approval.pgTid(), approval.paidAt());

        log.info("event=PAYMENT_PAID paymentId={} orderId={} amount={} method={}",
                payment.getId(), payment.getOrderId(), payment.getAmount(), payment.getMethod());

        return PaymentResult.from(payment);
    }
}
