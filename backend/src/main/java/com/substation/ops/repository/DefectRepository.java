package com.substation.ops.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.substation.ops.domain.Defect;
import com.substation.ops.dto.OverdueDefectRow;

public interface DefectRepository extends JpaRepository<Defect, Long> {

    List<Defect> findByBayIdOrderByDueDateAsc(Long bayId);

    /**
     * 【手写 JPQL 一】缺陷超期排行：未闭环且已过限期的缺陷，限期越早排越靠前。
     * 直接投影成 OverdueDefectRow；超期天数（datediff）由 service 用当天日期在 Java 侧补齐。
     */
    @Query("select new com.substation.ops.dto.OverdueDefectRow("
            + "d.id, d.defectNo, d.title, d.level, d.bayId, d.dueDate, d.status, 0) "
            + "from Defect d "
            + "where d.status <> :closed and d.dueDate < :today "
            + "order by d.dueDate asc")
    List<OverdueDefectRow> rankOverdue(@Param("closed") String closed, @Param("today") LocalDate today);

    /** 某间隔尚未闭环的缺陷数 */
    long countByBayIdAndStatusNot(Long bayId, String closed);
}
