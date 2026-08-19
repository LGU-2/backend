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

    @Modifying
    @Query("update Member m set m.refreshTokenHash = :newHash, m.refreshTokenExpiresAt = :expiresAt "
            + "where m.id = :id and m.refreshTokenHash = :oldHash")
    int compareAndSetRefreshToken(@Param("id") Long id, @Param("oldHash") String oldHash,
                                   @Param("newHash") String newHash, @Param("expiresAt") LocalDateTime expiresAt);

    // (2026-08-19) MemberWithdrawalService.withdraw()가 카카오 재인증(동기 호출)을 트랜잭션 밖으로
    // 빼면서, 그 뒤의 DB 쓰기가 더 이상 findById()로 로드해둔 엔티티의 dirty checking에 기댈 수
    // 없게 됐다(트랜잭션 밖에서 로드한 엔티티는 detached라 변경해도 flush 안 됨) — 그래서
    // Member.withdraw()로 엔티티를 바꾸는 대신 이 명시적 UPDATE로 직접 반영한다. status <>
    // WITHDRAWN 조건은 이미 위(MemberWithdrawalCompletionService 호출 전)에서 한 번 걸렀지만,
    // 방어적으로 한 번 더 둔다.
    @Modifying
    @Query("update Member m set m.status = com.freshmarket.member.domain.entity.MemberStatus.WITHDRAWN, "
            + "m.deletedAt = :deletedAt where m.id = :id and m.status <> com.freshmarket.member.domain.entity.MemberStatus.WITHDRAWN")
    int markWithdrawn(@Param("id") Long id, @Param("deletedAt") LocalDateTime deletedAt);
}
