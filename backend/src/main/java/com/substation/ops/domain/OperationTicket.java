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
 * 操作票：倒闸操作的书面依据，票内含一组必须按顺序执行的操作步骤。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "operation_ticket")
public class OperationTicket {

    /** 票面状态：待执行 / 执行中 / 已完成 / 作废 */
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_FINISHED = "FINISHED";
    public static final String STATUS_CANCELED = "CANCELED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bay_id")
    private Long bayId;

    @Column(name = "ticket_no", length = 64)
    private String ticketNo;

    /** 业务日期 */
    @Column(name = "issue_date")
    private LocalDate issueDate;

    /** 操作任务，如「110kV 城东变 1 号主变由运行转检修」 */
    @Column(name = "task_name", length = 255)
    private String taskName;

    /** 发令人 */
    @Column(name = "dispatcher", length = 32)
    private String dispatcher;

    /** 监护人 */
    @Column(name = "guardian", length = 32)
    private String guardian;

    /** 操作人 */
    @Column(name = "operator_name", length = 32)
    private String operatorName;

    @Column(name = "status", length = 24)
    private String status;

    @Column(name = "remark", length = 255)
    private String remark;
}
