package com.substation.ops.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.substation.ops.domain.InspectionPlan;

public interface InspectionPlanRepository extends JpaRepository<InspectionPlan, Long> {

    List<InspectionPlan> findByPlanDateOrderByShiftAscIdAsc(LocalDate planDate);

    List<InspectionPlan> findByBayIdAndPlanDate(Long bayId, LocalDate planDate);

    /** 某站在一段时间内的巡检覆盖率辅助统计：按状态分组计数 */
    @Query("select p.status, count(p) from InspectionPlan p where p.planDate between :from and :to group by p.status")
    List<Object[]> countByStatus(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
