package com.substation.ops.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.NamedStoredProcedureQuery;
import jakarta.persistence.ParameterMode;
import jakarta.persistence.StoredProcedureParameter;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 存储过程 sp_equipment_summary_by_voltage 的结果映射。
 * 只用于接收存储过程返回的结果集，不对应真实物理表。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "equipment_summary")
@NamedStoredProcedureQuery(
        name = "EquipmentSummary.byVoltage",
        procedureName = "sp_equipment_summary_by_voltage",
        resultClasses = EquipmentSummary.class,
        parameters = {
                @StoredProcedureParameter(mode = ParameterMode.IN, name = "substationId", type = Long.class)
        })
public class EquipmentSummary {

    @Id
    @Column(name = "stat_id")
    private Long statId;

    @Column(name = "voltage_level")
    private String voltageLevel;

    /** 该电压等级下设备总台数 */
    @Column(name = "equip_total")
    private Integer equipTotal;

    /** 运行台数 */
    @Column(name = "running_total")
    private Integer runningTotal;

    /** 待检台数：上次检修距今超过定检周期或从未检修 */
    @Column(name = "pending_total")
    private Integer pendingTotal;

    /** 该电压等级下未闭环缺陷数 */
    @Column(name = "open_defect_total")
    private Integer openDefectTotal;
}
