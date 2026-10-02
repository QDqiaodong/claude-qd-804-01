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
 * 缺陷：由巡检/日常监视发现，走「登记 → 派工 → 处理 → 待验收 → 验收」五步闭环。
 *
 * 处理与验收是两件事：
 *  - 处理人只能「报完工」（提交处理结果），提交后进入 {@link #STATUS_PENDING_ACCEPTANCE}，自己不能验收；
 *  - 必须由另一名值班员填写验收结论，通过才闭环（{@link #STATUS_ACCEPTED}）；
 *  - 驳回则退回 {@link #STATUS_PROCESSING} 返工，本轮处理/验收人/驳回原因全部保留，
 *    历次处理与验收明细见 {@link DefectClosureRound}。
 *
 * 只有真正验收通过才计为闭环：超期排行、设备未闭环缺陷数、工作票终结闸门
 * 一律以 status = ACCEPTED 为准，待验收/驳回返工的缺陷仍算未闭环。
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

    /** 闭环状态：登记 → 派工 → 处理 → 待验收 → 验收（验收通过才闭环；驳回退回处理中） */
    public static final String STATUS_REGISTERED = "REGISTERED";
    public static final String STATUS_DISPATCHED = "DISPATCHED";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_PENDING_ACCEPTANCE = "PENDING_ACCEPTANCE";
    public static final String STATUS_ACCEPTED = "ACCEPTED";

    /** 验收结论 */
    public static final String ACCEPT_PASS = "PASS";
    public static final String ACCEPT_REJECT = "REJECT";

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

    /** 消缺负责人 */
    @Column(name = "owner", length = 32)
    private String owner;

    @Column(name = "status", length = 24)
    private String status;

    // ---------------- 最近一次处理（报完工）信息 ----------------

    /** 处理结果说明（最近一次提交；驳回返工后重新提交会覆盖，历次内容见 defect_closure_round） */
    @Column(name = "process_result", length = 512)
    private String processResult;

    /** 处理人（报完工提交人，最近一次） */
    @Column(name = "process_person", length = 32)
    private String processPerson;

    /** 提交处理结果时间（最近一次） */
    @Column(name = "process_time")
    private LocalDateTime processTime;

    // ---------------- 最近一次验收信息 ----------------

    /** 验收意见（通过备注或驳回原因，最近一次） */
    @Column(name = "accept_result", length = 512)
    private String acceptResult;

    /** 验收人（必须与处理人不同） */
    @Column(name = "accept_person", length = 32)
    private String acceptPerson;

    /** 验收时间（最近一次；通过时同时写入闭环时间） */
    @Column(name = "accept_time")
    private LocalDateTime acceptTime;

    /** 累计驳回返工次数 */
    @Column(name = "reject_count")
    private Integer rejectCount;

    /** 闭环（验收通过）时间 */
    @Column(name = "closed_time")
    private LocalDateTime closedTime;
}
