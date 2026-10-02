package com.substation.ops.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.substation.ops.domain.Defect;
import com.substation.ops.dto.OverdueDefectRow;

public interface DefectRepository extends JpaRepository<Defect, Long> {

    List<Defect> findByBayIdOrderByDueDateAsc(Long bayId);

    /**
     * 行级悲观锁：派工、开工、提交处理结果、验收等所有会推进缺陷闭环的动作都先取锁。
     * 处理人重复点击提交、两名值班员同时验收同一缺陷时串行化：
     * 后提交者在锁内重新读到最新阶段并被状态重检拦下，只能生效一次。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Defect d where d.id = :id")
    Optional<Defect> findByIdForUpdate(@Param("id") Long id);

    /**
     * 【手写 JPQL 一】缺陷超期排行：未闭环且已过限期的缺陷，限期越早排越靠前。
     * 直接投影成 OverdueDefectRow；超期天数（datediff）由 service 用当天日期在 Java 侧补齐。
     * 待验收（PENDING_ACCEPTANCE）同样是未闭环，仍在排行内，不会因为「报完工」就消失。
     */
    @Query("select new com.substation.ops.dto.OverdueDefectRow("
            + "d.id, d.defectNo, d.title, d.level, d.bayId, d.dueDate, d.status, 0) "
            + "from Defect d "
            + "where d.status <> :closed and d.dueDate < :today "
            + "order by d.dueDate asc")
    List<OverdueDefectRow> rankOverdue(@Param("closed") String closed, @Param("today") LocalDate today);

    /** 某间隔尚未闭环的缺陷数（待验收、驳回返工都算未闭环） */
    long countByBayIdAndStatusNot(Long bayId, String closed);

    /**
     * 某间隔超期仍未闭环的缺陷（工作票终结闸门、超期排行共用同一口径：
     * 只有验收通过 ACCEPTED 才解除阻断，待验收/驳回返工一律不放行）。
     */
    @Query("select d from Defect d "
            + "where d.bayId = :bayId and d.status <> :closed and d.dueDate < :today "
            + "order by d.dueDate asc")
    List<Defect> findOpenOverdueByBay(@Param("bayId") Long bayId,
                                      @Param("closed") String closed,
                                      @Param("today") LocalDate today);
}
