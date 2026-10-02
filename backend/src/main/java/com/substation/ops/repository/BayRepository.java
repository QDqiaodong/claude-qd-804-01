package com.substation.ops.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.substation.ops.domain.Bay;

public interface BayRepository extends JpaRepository<Bay, Long> {

    List<Bay> findBySubstationIdOrderBySortNoAsc(Long substationId);

    /** 按间隔类型 + 状态组合筛选（用于平面示意图侧边过滤） */
    @Query("select b from Bay b where b.bayType = :bayType and b.status = :status order by b.sortNo asc")
    List<Bay> findByTypeAndStatus(@Param("bayType") String bayType, @Param("status") String status);
}
