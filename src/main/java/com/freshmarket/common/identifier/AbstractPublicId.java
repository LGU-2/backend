package com.freshmarket.common.identifier;

import java.util.Objects;
import java.util.UUID;

/* 내부 ID(Long)와 외부 ID(UUID)가 원시 타입이면 뒤바꿔 넣어도 컴파일이 통과한다.
   잘못된 엔티티의 UUID 를 넘겨도 조회 결과가 비었을 뿐 원인이 드러나지 않는다. */
public abstract class AbstractPublicId {

    private final UUID value;

    protected AbstractPublicId(UUID value) {
        this.value = Objects.requireNonNull(value, "value must not be null");
    }

    public UUID value() {
        return value;
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        /* instanceof 가 아니라 getClass() 로 비교한다.
           서로 다른 엔티티의 식별자가 같은 UUID 값을 가질 때 동등하다고 판정되면
           타입으로 막으려던 혼동이 다시 열린다. */
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        return value.equals(((AbstractPublicId) o).value);
    }

    @Override
    public final int hashCode() {
        return value.hashCode();
    }

    @Override
    public final String toString() {
        return value.toString();
    }
}
