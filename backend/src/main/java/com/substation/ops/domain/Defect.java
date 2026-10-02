package com.substation.ops.domain;

import java.time.LocalDate;
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
 * 缺陷：由巡检/日常监视发现，走「登记 → 派工 → 处理 → 待验收 → 验收闭环」五步。
 * 处理人只能报完工（提交处理结果进入待验收），不能替自己验收；
 * 验收由另一名值班员独立填写结论：通过才闭环，驳回则退回处理中返工。
 * 超期未消缺会连锁阻止该间隔的工作票终结。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "defect")
public class Defect {

    /** 缺陷等级 */
    public static final String LEVEL_URGENT = "URGENT";
    public static final String LEVEL_MAJOR = "MAJOR";
    public static final String LEVEL_MINOR = "MINOR";

    /**
     * 消缺闭环状态：登记 → 派工 → 处理 → 待验收 → 已验收。
     * 注意：处理（PROCESSING）与验收（ACCEPTED）之间必须经过独立的待验收环节，
     * 待验收缺陷仍算未闭环，不能从超期排行消失，也不能放开关联工作票终结。
     */
    public static final String STATUS_REGISTERED = "REGISTERED";
    public static final String STATUS_DISPATCHED = "DISPATCHED";
    public static final String STATUS_PROCESSING = "PROCESSING";
    /** 处理人已提交处理结果，等待另一名值班员验收 */
    public static final String STATUS_PENDING_ACCEPTANCE = "PENDING_ACCEPTANCE";
    /** 验收通过，闭环（终态） */
    public static final String STATUS_ACCEPTED = "ACCEPTED";

    /** 各等级对应的标准消缺期限（天） */
    public static final int DUE_DAYS_URGENT = 1;
    public static final int DUE_DAYS_MAJOR = 7;
    public static final int DUE_DAYS_MINOR = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bay_id")
    private Long bayId;

    @Column(name = "equipment_id")
    private Long equipmentId;

    @Column(name = "defect_no", length = 32)
    private String defectNo;

    @Column(name = "title", length = 128)
    private String title;

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "level", length = 16)
    private String level;

    /** 发现时间 */
    @Column(name = "found_time")
    private LocalDateTime foundTime;

    /** 发现人（通常是当值运行人员） */
    @Column(name = "reporter", length = 32)
    private String reporter;

    /** 限期消缺日期 */
    @Column(name = "due_date")
    private LocalDate dueDate;

    /** 消缺负责人（处理人） */
    @Column(name = "owner", length = 32)
    private String owner;

    @Column(name = "status", length = 24)
    private String status;

    /** 最近一次提交的处理结果（驳回返工时仍保留，便于验收人复核） */
    @Column(name = "process_result", length = 512)
    private String processResult;

    /** 最近一次处理结果提交人（必须是消缺负责人） */
    @Column(name = "processed_by", length = 32)
    private String processedBy;

    /** 最近一次处理结果提交时间 */
    @Column(name = "processed_time")
    private LocalDateTime processedTime;

    /** 累计返工次数（每次验收驳回 +1） */
    @Column(name = "rework_count")
    private Integer reworkCount;

    /** 验收通过人（验收闭环的验收人，须与处理人不同） */
    @Column(name = "accepted_by", length = 32)
    private String acceptedBy;

    /** 闭环（验收通过）时间 */
    @Column(name = "closed_time")
    private LocalDateTime closedTime;
}
