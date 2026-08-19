package com.freshmarket.member.domain.repository;

import com.freshmarket.member.domain.entity.Member;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByActiveProviderKey(String activeProviderKey);

    @Modifying
    @Query("update Member m set m.refreshTokenHash = :hash, m.refreshTokenExpiresAt = :expiresAt where m.id = :id")
    int updateRefreshToken(@Param("id") Long id, @Param("hash") String hash, @Param("expiresAt") LocalDateTime expiresAt);

    @Modifying
    @Query("update Member m set m.refreshTokenHash = null, m.refreshTokenExpiresAt = null where m.id = :id")
    int clearRefreshToken(@Param("id") Long id);

    // (2026-08-19) opaque 토큰 전환으로 compareAndSetRefreshToken(예전 reissue()의 "Redis 장애 시
    // DB CAS로 폴백" 경로 전용)을 제거했다 — opaque 토큰은 Redis가 없으면 이 토큰이 누구 건지
    // 자체를 알 수 없어(memberId를 못 구해서) 이 메서드를 호출할 방법이 없어졌다. 이유는
    // MemberTokenService 클래스 주석 참고.

    // (2026-08-19) MemberWithdrawalService.withdraw()가 카카오 재인증(동기 호출)을 트랜잭션 밖으로
    // 빼면서, 그 뒤의 DB 쓰기가 더 이상 findById()로 로드해둔 엔티티의 dirty checking에 기댈 수
    // 없게 됐다(트랜잭션 밖에서 로드한 엔티티는 detached라 변경해도 flush 안 됨) — 그래서
    // Member.withdraw()로 엔티티를 바꾸는 대신 이 명시적 UPDATE로 직접 반영한다. status <>
    // WITHDRAWN 조건은 이미 위(MemberWithdrawalCompletionService 호출 전)에서 한 번 걸렀지만,
    // 방어적으로 한 번 더 둔다.
    // clearAutomatically=true: 벌크 UPDATE는 영속성 컨텍스트(1차 캐시)를 안 거치고 DB에 바로
    // 반영된다 — 이 값 없이 같은 트랜잭션에서 findById()로 다시 읽으면 방금 update한 값이 아니라
    // 캐시에 남은 이전 상태(예: status=PENDING_PROFILE)가 그대로 보일 수 있다(Hibernate가
    // 영속성 컨텍스트에 이미 있는 엔티티는 재조회 없이 그대로 반환하기 때문). 이 프로젝트의
    // 다른 @Modifying 메서드(updateRefreshToken 등)엔 이 옵션이 없는데, 그것들은 지금까지 호출부가
    // 같은 트랜잭션에서 그 엔티티를 다시 읽지 않아 드러나지 않았을 뿐이라 별개로 점검이 필요하다.
    @Modifying(clearAutomatically = true)
    @Query("update Member m set m.status = com.freshmarket.member.domain.entity.MemberStatus.WITHDRAWN, "
            + "m.deletedAt = :deletedAt where m.id = :id and m.status <> com.freshmarket.member.domain.entity.MemberStatus.WITHDRAWN")
    int markWithdrawn(@Param("id") Long id, @Param("deletedAt") LocalDateTime deletedAt);
}
