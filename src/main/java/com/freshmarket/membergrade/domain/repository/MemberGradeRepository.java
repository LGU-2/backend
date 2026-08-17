package com.freshmarket.membergrade.domain.repository;

import com.freshmarket.membergrade.domain.entity.MemberGrade;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberGradeRepository extends JpaRepository<MemberGrade, Long> {

    Optional<MemberGrade> findByIsDefaultTrue();

    boolean existsByName(String name);
}
