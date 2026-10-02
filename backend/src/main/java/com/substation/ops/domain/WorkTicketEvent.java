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
 * 工作票事件流水：签发 / 许可 / 撤回许可 / 通知拆除 / 终结 / 作废。
 * 只增不改，刷新与重新进入后仍可完整还原票的真实经过与当前阶段。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "work_ticket_event")
public class WorkTicketEvent {

    public static final String TYPE_ISSUE = "ISSUE";
    public static final String TYPE_PERMIT = "PERMIT";
    public static final String TYPE_WITHDRAW = "WITHDRAW";
    public static final String TYPE_START_REMOVE = "START_REMOVE";
    public static final String TYPE_TERMINATE = "TERMINATE";
    public static final String TYPE_CANCEL = "CANCEL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(name = "event_type", length = 24, nullable = false)
    private String eventType;

    @Column(name = "operator_name", length = 32)
    private String operatorName;

    @Column(name = "event_time", nullable = false)
    private LocalDateTime eventTime;

    @Column(name = "detail", length = 512)
    private String detail;
}
