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
     * 【手写 JPQL 一】缺陷超期排行：未闭环且已过限期的缺陷，限期越早排越靠前。
     * 直接投影成 OverdueDefectRow；超期天数（datediff）由 service 用当天日期在 Java 侧补齐。
     *
     * 闭环只认验收通过（ACCEPTED）：待验收、驳回返工等未真正闭环的缺陷照样上榜。
     */
    @Query("select new com.substation.ops.dto.OverdueDefectRow("
            + "d.id, d.defectNo, d.title, d.level, d.bayId, d.dueDate, d.status, 0) "
            + "from Defect d "
            + "where d.status <> :closed and d.dueDate < :today "
            + "order by d.dueDate asc")
    List<OverdueDefectRow> rankOverdue(@Param("closed") String closed, @Param("today") LocalDate today);

    /** 某间隔尚未闭环（未验收通过）的缺陷数 */
    long countByBayIdAndStatusNot(Long bayId, String closed);

    /**
     * 行级悲观锁：报完工、验收等所有推进闭环的动作都先取锁，
     * 重复提交、两名值班员同时验收时自动串行，只有一次有效推进。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Defect d where d.id = :id")
    Optional<Defect> findByIdForUpdate(@Param("id") Long id);
}
