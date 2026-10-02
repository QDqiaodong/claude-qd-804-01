package com.substation.ops.domain;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 设备台账：主变、断路器、隔离开关、CT/PT、避雷器。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "equipment")
public class Equipment {

    public static final String TYPE_TRANSFORMER = "TRANSFORMER";
    public static final String TYPE_BREAKER = "BREAKER";
    public static final String TYPE_DISCONNECTOR = "DISCONNECTOR";
    public static final String TYPE_CT_PT = "CT_PT";
    public static final String TYPE_ARRESTER = "ARRESTER";

    /** 设备状态：运行 / 备用 / 检修 / 缺陷运行 */
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_STANDBY = "STANDBY";
    public static final String STATUS_MAINTENANCE = "MAINTENANCE";
    public static final String STATUS_DEFECTIVE = "DEFECTIVE";

    /** 年度定检周期（天）：超过此天数未检即为待检 */
    public static final int CHECK_PERIOD_DAYS = 365;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bay_id")
    private Long bayId;

    @Column(name = "equipment_no", length = 32)
    private String equipmentNo;

    @Column(name = "name", length = 64)
    private String name;

    @Column(name = "equip_type", length = 32)
    private String equipType;

    /** 型号 */
    @Column(name = "model", length = 64)
    private String model;

    /** 生产厂家 */
    @Column(name = "vendor", length = 64)
    private String vendor;

    /** 投运日期 */
    @Column(name = "commission_date")
    private LocalDate commissionDate;

    /** 上次检修日期 */
    @Column(name = "last_check_date")
    private LocalDate lastCheckDate;

    @Column(name = "status", length = 24)
    private String status;

    @Column(name = "remark", length = 255)
    private String remark;
}
