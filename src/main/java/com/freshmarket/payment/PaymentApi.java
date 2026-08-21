package com.freshmarket.payment;

// order가 결제를 시작할 때 쓰는 payment 도메인의 공개 창구다.
public interface PaymentApi {

    PaymentResult requestPayment(PaymentRequest request);
}
