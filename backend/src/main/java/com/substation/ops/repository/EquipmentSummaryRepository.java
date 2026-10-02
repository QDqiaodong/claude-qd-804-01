package com.substation.ops.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.query.Procedure;
import org.springframework.data.repository.query.Param;

import com.substation.ops.domain.EquipmentSummary;

public interface EquipmentSummaryRepository extends JpaRepository<EquipmentSummary, Long> {

    /**
     * 【存储过程】调用 sp_equipment_summary_by_voltage：按电压等级汇总设备台数与待检数。
     * p_substationId 传 0 表示统计全部变电站。
     */
    @Procedure(name = "EquipmentSummary.byVoltage")
    List<EquipmentSummary> summaryByVoltage(@Param("substationId") Long substationId);
}
