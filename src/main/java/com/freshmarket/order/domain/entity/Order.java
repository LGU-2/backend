package com.freshmarket.order.domain.entity;

import com.freshmarket.common.entity.BaseMutableTimeEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "orders")
@AttributeOverride(name = "id", column = @Column(name = "order_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseMutableTimeEntity {

    @Column(name = "order_no", nullable = false, length = 30)
    private String orderNo;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OrderStatus status;

    @Column(name = "product_amount", nullable = false)
    private int productAmount;

    @Column(name = "discount_amount", nullable = false)
    private int discountAmount;

    @Column(name = "member_coupon_id")
    private Long memberCouponId;

    // coupon 도메인의 내부 enum에 의존하지 않도록 DB 스냅샷 값은 문자열로 보관한다.
    @Column(name = "coupon_scope", length = 20)
    private String couponScope;

    @Column(name = "coupon_discount", nullable = false)
    private int couponDiscount;

    @Column(name = "shipping_fee", nullable = false)
    private int shippingFee;

    @Column(name = "total_amount", nullable = false)
    private int totalAmount;

    @Column(name = "ship_recipient", nullable = false, length = 50)
    private String shipRecipient;

    @Column(name = "ship_phone", nullable = false, length = 20)
    private String shipPhone;

    @Column(name = "ship_zipcode", nullable = false, length = 10)
    private String shipZipcode;

    @Column(name = "ship_address", nullable = false, length = 500)
    private String shipAddress;

    @Column(name = "ship_message", length = 255)
    private String shipMessage;

    @Column(name = "ordered_at", nullable = false)
    private LocalDateTime orderedAt;

    private Order(Long memberId, String orderNo, OrderStatus status, int productAmount,
                  int discountAmount, Long memberCouponId, String couponScope, int couponDiscount,
                  int shippingFee, int totalAmount, String shipRecipient, String shipPhone,
                  String shipZipcode, String shipAddress, String shipMessage, LocalDateTime orderedAt) {
        this.memberId = memberId;
        this.orderNo = orderNo;
        this.status = status;
        this.productAmount = productAmount;
        this.discountAmount = discountAmount;
        this.memberCouponId = memberCouponId;
        this.couponScope = couponScope;
        this.couponDiscount = couponDiscount;
        this.shippingFee = shippingFee;
        this.totalAmount = totalAmount;
        this.shipRecipient = shipRecipient;
        this.shipPhone = shipPhone;
        this.shipZipcode = shipZipcode;
        this.shipAddress = shipAddress;
        this.shipMessage = shipMessage;
        this.orderedAt = orderedAt;
    }

    // 주문 생성 기능이 붙기 전 조회 단위 테스트와 초기 생성 서비스가 함께 쓰는 생성 경로다.
    public static Order place(Long memberId, String orderNo, OrderStatus status, int productAmount,
                              int discountAmount, Long memberCouponId, String couponScope,
                              int couponDiscount, int shippingFee, int totalAmount, String shipRecipient,
                              String shipPhone, String shipZipcode, String shipAddress,
                              String shipMessage, LocalDateTime orderedAt) {
        return new Order(memberId, orderNo, status, productAmount, discountAmount, memberCouponId,
                couponScope, couponDiscount, shippingFee, totalAmount, shipRecipient, shipPhone,
                shipZipcode, shipAddress, shipMessage, orderedAt);
    }
}
