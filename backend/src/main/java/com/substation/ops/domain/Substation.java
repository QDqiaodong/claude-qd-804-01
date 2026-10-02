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
 * 变电站：运行管理的最上层单位（示例：城东变、河西变……）。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "substation")
public class Substation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", length = 32)
    private String code;

    @Column(name = "name", length = 64)
    private String name;

    /** 管辖地市 / 片区 */
    @Column(name = "area", length = 64)
    private String area;

    /** 调度单位 */
    @Column(name = "dispatch_org", length = 64)
    private String dispatchOrg;

    /** 值守方式：有人值守 / 无人值守 */
    @Column(name = "watch_mode", length = 32)
    private String watchMode;

    /** 投运日期 */
    @Column(name = "commission_date")
    private LocalDate commissionDate;

    @Column(name = "remark", length = 255)
    private String remark;
}
