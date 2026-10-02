package com.substation.ops.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.NamedStoredProcedureQuery;
import jakarta.persistence.ParameterMode;
import jakarta.persistence.StoredProcedureParameter;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 工作票：变电站一切检修作业的开工资格凭证。
 * 三审：签发人 → 许可人 → 终结人，三人不得重复。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "work_ticket")
@NamedStoredProcedureQuery(
        name = "WorkTicket.nextTicketNo",
        procedureName = "sp_next_work_ticket_no",
        parameters = {
                @StoredProcedureParameter(mode = ParameterMode.IN, name = "bayId", type = Long.class),
                @StoredProcedureParameter(mode = ParameterMode.IN, name = "issueDate", type = String.class),
                @StoredProcedureParameter(mode = ParameterMode.OUT, name = "ticketNo", type = String.class)
        })
public class WorkTicket {

    /** 票面状态：待签发 → 已签发 → 已许可 → 已终结 / 作废 */
    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_ISSUED = "ISSUED";
    public static final String STATUS_PERMITTED = "PERMITTED";
    public static final String STATUS_TERMINATED = "TERMINATED";
    public static final String STATUS_CANCELED = "CANCELED";

    /** 三审环节 */
    public static final String STAGE_ISSUE = "ISSUE";
    public static final String STAGE_PERMIT = "PERMIT";
    public static final String STAGE_TERMINATE = "TERMINATE";

    /** 许可后子阶段：作业中 → 安措拆除恢复中（撤回许可则回到已签发） */
    public static final String PHASE_WORKING = "WORKING";
    public static final String PHASE_DEMOLISHING = "DEMOLISHING";

    /** 票种：第一种（停电）/ 第二种（不停电） */
    public static final String KIND_POWER_CUT = "POWER_CUT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bay_id")
    private Long bayId;

    @Column(name = "ticket_no", length = 64)
    private String ticketNo;

    /** 业务日期：对应前端顶部时间轴选中的那天 */
    @Column(name = "issue_date")
    private LocalDate issueDate;

    /** 工作负责人 */
    @Column(name = "work_leader", length = 32)
    private String workLeader;

    /** 工作班成员，逗号分隔 */
    @Column(name = "team_members", length = 255)
    private String teamMembers;

    /** 工作内容 */
    @Column(name = "work_content", length = 512)
    private String workContent;

    /** 停电范围 */
    @Column(name = "power_cut_range", length = 512)
    private String powerCutRange;

    /** 安全措施 */
    @Column(name = "safety_measures", length = 512)
    private String safetyMeasures;

    /** 计划开工 / 完工时间 */
    @Column(name = "plan_start_time")
    private LocalDateTime planStartTime;

    @Column(name = "plan_end_time")
    private LocalDateTime planEndTime;

    // ---------------- 三审 ----------------
    /** 签发人 */
    @Column(name = "issuer", length = 32)
    private String issuer;

    /** 许可人 */
    @Column(name = "permittor", length = 32)
    private String permittor;

    /** 终结人 */
    @Column(name = "terminator", length = 32)
    private String terminator;

    @Column(name = "issue_time")
    private LocalDateTime issueTime;

    @Column(name = "permit_time")
    private LocalDateTime permitTime;

    @Column(name = "terminate_time")
    private LocalDateTime terminateTime;

    /** 许可后子阶段：WORKING 作业中 / DEMOLISHING 安措拆除恢复中；撤回许可后置空 */
    @Column(name = "permit_phase", length = 24)
    private String permitPhase;

    @Column(name = "status", length = 24)
    private String status;

    @Column(name = "remark", length = 255)
    private String remark;
}
