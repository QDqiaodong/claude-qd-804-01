package com.substation.ops.domain;

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
 * 间隔（间隔单元）：变电站一次系统的最小停电管理单元。
 * 状态直接影响能否对它下达工作票 —— 运行中的间隔不允许检修类工作票。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "bay")
public class Bay {

    /** 间隔类型 */
    public static final String TYPE_MAIN_TRANSFORMER = "MAIN_TRANSFORMER";
    public static final String TYPE_OUTGOING = "OUTGOING";
    public static final String TYPE_CAPACITOR = "CAPACITOR";
    public static final String TYPE_BUS_TIE = "BUS_TIE";

    /** 间隔状态 */
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_HOT_STANDBY = "HOT_STANDBY";
    public static final String STATUS_COLD_STANDBY = "COLD_STANDBY";
    public static final String STATUS_MAINTENANCE = "MAINTENANCE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "substation_id")
    private Long substationId;

    @Column(name = "code", length = 32)
    private String code;

    @Column(name = "name", length = 64)
    private String name;

    /** 间隔类型：主变 / 出线 / 电容器 / 母联 */
    @Column(name = "bay_type", length = 32)
    private String bayType;

    /** 电压等级：110kV / 35kV / 10kV */
    @Column(name = "voltage_level", length = 16)
    private String voltageLevel;

    /** 间隔状态：运行 / 热备 / 冷备 / 检修 */
    @Column(name = "status", length = 24)
    private String status;

    /** 平面示意图上的排序位置 */
    @Column(name = "sort_no")
    private Integer sortNo;

    @Column(name = "remark", length = 255)
    private String remark;
}
