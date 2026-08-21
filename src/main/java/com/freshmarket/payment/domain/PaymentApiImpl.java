package com.freshmarket.payment.domain;

import com.freshmarket.payment.PaymentApi;
import com.freshmarket.payment.PaymentInfo;
import com.freshmarket.payment.PaymentRequest;
import com.freshmarket.payment.PaymentResult;
import com.freshmarket.payment.domain.client.PaymentGateway;
import com.freshmarket.payment.domain.entity.Payment;
import com.freshmarket.payment.domain.service.PaymentService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 공개 API는 트랜잭션을 열지 않고, 짧은 DB 트랜잭션과 외부 PG 호출의 경계를 조립만 한다.
@Component
@RequiredArgsConstructor
class PaymentApiImpl implements PaymentApi {

    private final PaymentService paymentService;
    private final PaymentGateway paymentGateway;

    @Override
    public PaymentResult requestPayment(PaymentRequest request) {
        PaymentPreparation preparation = paymentService.preparePayment(request);
        Payment payment = preparation.payment();
        if (!preparation.newlyPrepared()) {
            return PaymentResult.from(payment);
        }
        return paymentService.approvePayment(payment.getId(), paymentGateway.request(payment.toRequest()));
    }

    @Override
    public Optional<PaymentInfo> findPaymentInfo(Long orderId) {
        return paymentService.findPaymentInfo(orderId);
    }
}
