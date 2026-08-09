package com.freshmarket.verification;

import com.freshmarket.common.entity.BasePublicMutableTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/* 검증 전용 엔티티다. 프로덕션 도메인이 아니라 이 소스셋에만 있다.
   공통 베이스가 실제로 매핑되는지, public_id 가 어떤 컬럼 타입으로 생성되는지 확인하는 데만 쓴다. */
@Entity
@Table(name = "verification_sample")
public class VerificationSample extends BasePublicMutableTimeEntity {

    @Column(name = "label", nullable = false, length = 50)
    private String label;

    /* columnDefinition 도 AttributeConverter 도 없는 맨 UUID 다.
       Hibernate 기본 매핑만으로 어떤 컬럼이 나오는지 분리해서 보기 위한 필드다. */
    @Column(name = "bare_uuid")
    private UUID bareUuid;

    protected VerificationSample() {
    }

    private VerificationSample(String label) {
        this.label = label;
    }

    public static VerificationSample of(String label) {
        return new VerificationSample(label);
    }

    public String getLabel() {
        return label;
    }

    public UUID publicId() {
        return publicIdValue();
    }
}
