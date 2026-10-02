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
 * 缺陷：由巡检/日常监视发现，走「登记 → 派工 → 处理 → 验收」四步闭环。
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

    /** 消缺闭环状态：登记 → 派工 → 处理 → 验收（验收即闭环） */
    public static final String STATUS_REGISTERED = "REGISTERED";
    public static final String STATUS_DISPATCHED = "DISPATCHED";
    public static final String STATUS_PROCESSING = "PROCESSING";
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

    /** 消缺负责人 */
    @Column(name = "owner", length = 32)
    private String owner;

    @Column(name = "status", length = 24)
    private String status;

    /** 闭环（验收）时间 */
    @Column(name = "closed_time")
    private LocalDateTime closedTime;
}
