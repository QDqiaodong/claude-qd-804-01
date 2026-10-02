package com.substation.ops.domain;

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
 * 工作票安全措施（逐项确认执行闭环）。
 *
 * 一条措施的生命周期：
 *   PENDING 待执行 ──装设确认──▶ INSTALLED 已执行（已装设/已合上）
 *     ──通知拆除──▶ TO_REMOVE 待拆除 ──拆除确认──▶ REMOVED 已拆除
 *
 * 撤回许可：TO_REMOVE/REMOVED 回置为 PENDING（已发生的装拆人/时间不抹掉）；
 * 现场变更：原行 VOIDED 留痕，另起一行新版本号从 PENDING 开始，二者通过版本链关联。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "safety_measure")
public class SafetyMeasure {

    /** 措施类型 */
    public static final String TYPE_GROUND_LINE = "GROUND_LINE";
    public static final String TYPE_EARTH_SWITCH = "EARTH_SWITCH";
    public static final String TYPE_BARRIER = "BARRIER";
    public static final String TYPE_SIGNBOARD = "SIGNBOARD";
    public static final String TYPE_OTHER = "OTHER";

    /** 阶段 */
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_INSTALLED = "INSTALLED";
    public static final String STATUS_TO_REMOVE = "TO_REMOVE";
    public static final String STATUS_REMOVED = "REMOVED";
    /** 变更作废：保留原确认痕迹，不再参与装设/拆除核验 */
    public static final String STATUS_VOIDED = "VOIDED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    /** 票面序号，从 1 开始（版本化行沿用同一序号） */
    @Column(name = "seq_no", nullable = false)
    private Integer seqNo;

    /** 版本号，从 1 开始；现场变更后新版本自增 */
    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    /** 变更前原措施 id（版本链源头） */
    @Column(name = "parent_measure_id")
    private Long parentMeasureId;

    /** GROUND_LINE 接地线 / EARTH_SWITCH 接地刀闸 / BARRIER 遮栏 / SIGNBOARD 标示牌 / OTHER */
    @Column(name = "measure_type", length = 16, nullable = false)
    private String measureType;

    /** 所在间隔 */
    @Column(name = "location_bay_id", nullable = false)
    private Long locationBayId;

    /** 具体位置，如「2017 接地刀闸」「1号主变本体围栏」 */
    @Column(name = "location_desc", length = 255, nullable = false)
    private String locationDesc;

    /** 要求动作，如「装设接地线一组」「合上」「围设」「悬挂」 */
    @Column(name = "required_action", length = 255, nullable = false)
    private String requiredAction;

    /** 是否需要拆除恢复：验电、保持安全距离、专人监护等为 false */
    @Column(name = "need_restore", nullable = false)
    private Boolean needRestore;

    /** 装设/合上对应的操作票及步骤（可空：编制时尚未绑定） */
    @Column(name = "install_ticket_id")
    private Long installTicketId;

    @Column(name = "install_step_no")
    private Integer installStepNo;

    /** 拆除/拉开恢复对应的操作票及步骤 */
    @Column(name = "restore_ticket_id")
    private Long restoreTicketId;

    @Column(name = "restore_step_no")
    private Integer restoreStepNo;

    @Column(name = "install_person", length = 32)
    private String installPerson;

    @Column(name = "install_time")
    private LocalDateTime installTime;

    @Column(name = "remove_person", length = 32)
    private String removePerson;

    @Column(name = "remove_time")
    private LocalDateTime removeTime;

    /** PENDING / INSTALLED / TO_REMOVE / REMOVED / VOIDED */
    @Column(name = "status", length = 24, nullable = false)
    private String status;

    @Column(name = "void_reason", length = 255)
    private String voidReason;

    @Column(name = "remark", length = 255)
    private String remark;

    /** 行版本（并发刷新提示用），不参与业务判断 */
    @Column(name = "version", nullable = false)
    private Integer version;
}
