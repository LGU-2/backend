package com.freshmarket.member.domain.repository;

import com.freshmarket.member.domain.entity.Address;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// (2026-08-18 10:49) com.freshmarket.address.domain.repository에서 이동.
public interface AddressRepository extends JpaRepository<Address, Long> {

    // (2026-08-18 18:40) docs/api/member.md: "기본 배송지가 먼저 온다." 원래
    // findByMemberIdOrderByCreatedAtDesc는 등록 순서로만 정렬해 이 규칙을 안 지켰다(API 점검 중
    // 발견). isDefault를 정렬 파생 메서드 이름에 넣지 않고 @Query로 명시한 이유: 이 필드가
    // Lombok이 isDefault() 게터를 만드는 boolean이라 파생 쿼리 이름 파싱이 "default"로
    // 잘못 해석할 수 있는 알려진 함정이 있다 — JPQL로 쓰면 그 위험이 없다.
    @Query("select a from Address a where a.memberId = :memberId order by a.isDefault desc, a.createdAt desc")
    List<Address> findByMemberIdOrderedByDefaultFirst(@Param("memberId") Long memberId);

    Optional<Address> findByIdAndMemberId(Long id, Long memberId);

    long countByMemberId(Long memberId);

    @Modifying
    @Query("update Address a set a.isDefault = false where a.memberId = :memberId and a.isDefault = true")
    void clearDefaultForMember(Long memberId);
}
