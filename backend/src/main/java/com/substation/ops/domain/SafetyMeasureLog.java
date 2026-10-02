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
 * 安全措施执行痕迹：只增不改、不删除。
 * 撤回许可、现场变更都只能新增痕迹，已发生的装设/拆除记录永久保留。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "safety_measure_log")
public class SafetyMeasureLog {

    public static final String ACTION_CREATE = "CREATE";
    public static final String ACTION_INSTALL = "INSTALL";
    public static final String ACTION_REMOVE = "REMOVE";
    public static final String ACTION_VOID = "VOID";
    public static final String ACTION_REACTIVATE = "REACTIVATE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "measure_id", nullable = false)
    private Long measureId;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    /** CREATE / INSTALL / REMOVE / VOID / REACTIVATE */
    @Column(name = "action", length = 24, nullable = false)
    private String action;

    @Column(name = "from_status", length = 24)
    private String fromStatus;

    @Column(name = "to_status", length = 24)
    private String toStatus;

    @Column(name = "operator_name", length = 32)
    private String operatorName;

    @Column(name = "operate_time", nullable = false)
    private LocalDateTime operateTime;

    @Column(name = "detail", length = 512)
    private String detail;
}
