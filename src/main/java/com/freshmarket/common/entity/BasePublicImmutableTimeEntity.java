package com.freshmarket.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/* 외부에 단건으로 노출되는 이력 테이블의 베이스다.
   @Getter 를 붙이지 않는다. 생 UUID 를 돌려주는 public 접근자가 생겨 타입 래퍼를 우회한다 (BE-1-09). */
@MappedSuperclass
public abstract class BasePublicImmutableTimeEntity extends BaseImmutableTimeEntity {

    /* ORM 이 INSERT 직전에 채운다.
       style 을 생략하면 기본값 AUTO 가 RANDOM 으로 풀려 v4 가 된다 (IDS-5-02). */
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(name = "public_id", nullable = false, updatable = false,
            columnDefinition = "BINARY(16)")
    private UUID publicId;

    protected UUID publicIdValue() {
        return publicId;
    }
}
