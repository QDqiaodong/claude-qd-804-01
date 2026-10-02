package com.substation.ops.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.query.Procedure;
import org.springframework.data.repository.query.Param;

import com.substation.ops.domain.DefectSummary;

public interface DefectSummaryRepository extends JpaRepository<DefectSummary, Long> {

    /**
     * 【存储过程】调用 sp_defect_summary：统计各变电站当月缺陷数与消缺率。
     * p_substationId 传 0 表示统计全部变电站。
     */
    @Procedure(name = "DefectSummary.byMonth")
    List<DefectSummary> summaryByMonth(@Param("substationId") Long substationId, @Param("month") String month);
}
