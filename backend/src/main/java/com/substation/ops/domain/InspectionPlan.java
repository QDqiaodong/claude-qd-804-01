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
 * 巡检计划：按「某天 + 某班 + 某间隔」安排的一次例行/特巡任务。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "inspection_plan")
public class InspectionPlan {

    /** 班次 */
    public static final String SHIFT_DAY = "DAY";
    public static final String SHIFT_NIGHT = "NIGHT";

    /** 计划状态：待执行 / 已执行 / 已顺延 */
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_DONE = "DONE";
    public static final String STATUS_DELAYED = "DELAYED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bay_id")
    private Long bayId;

    /** 计划日期（与前端顶部时间轴强绑定） */
    @Column(name = "plan_date")
    private LocalDate planDate;

    /** 班次：DAY / NIGHT */
    @Column(name = "shift", length = 16)
    private String shift;

    /** 巡检性质：例行 / 特巡 / 夜巡 / 保电 */
    @Column(name = "patrol_type", length = 24)
    private String patrolType;

    /** 值班负责人 */
    @Column(name = "inspector", length = 32)
    private String inspector;

    @Column(name = "status", length = 24)
    private String status;

    @Column(name = "remark", length = 255)
    private String remark;
}
