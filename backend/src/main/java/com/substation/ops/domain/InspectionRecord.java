package com.substation.ops.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
 * 巡检记录：一次某间隔具体落地执行留下的抄录结果。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "inspection_record")
public class InspectionRecord {

    /** 结论：正常 / 异常（发现缺陷时通常同步登记一张缺陷单） */
    public static final String RESULT_NORMAL = "NORMAL";
    public static final String RESULT_ABNORMAL = "ABNORMAL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id")
    private Long planId;

    @Column(name = "bay_id")
    private Long bayId;

    /** 巡检完成时间 */
    @Column(name = "check_time")
    private LocalDateTime checkTime;

    @Column(name = "inspector", length = 32)
    private String inspector;

    @Column(name = "result_type", length = 24)
    private String resultType;

    /** 该间隔当日负荷率（%） */
    @Column(name = "load_rate", precision = 6, scale = 2)
    private BigDecimal loadRate;

    /** 红外测温最高点温度（℃） */
    @Column(name = "max_temperature", precision = 6, scale = 2)
    private BigDecimal maxTemperature;

    @Column(name = "remark", length = 255)
    private String remark;
}
