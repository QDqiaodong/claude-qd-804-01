package com.substation.ops.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.NamedStoredProcedureQuery;
import jakarta.persistence.ParameterMode;
import jakarta.persistence.StoredProcedureParameter;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 存储过程 sp_defect_summary 的结果映射（@NamedStoredProcedureQuery + resultClasses）。
 * 只用于接收存储过程返回的结果集，不对应真实物理表。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "defect_summary")
@NamedStoredProcedureQuery(
        name = "DefectSummary.byMonth",
        procedureName = "sp_defect_summary",
        resultClasses = DefectSummary.class,
        parameters = {
                @StoredProcedureParameter(mode = ParameterMode.IN, name = "substationId", type = Long.class),
                @StoredProcedureParameter(mode = ParameterMode.IN, name = "month", type = String.class)
        })
public class DefectSummary {

    @Id
    @Column(name = "stat_id")
    private Long statId;

    @Column(name = "substation_id")
    private Long substationId;

    @Column(name = "substation_name")
    private String substationName;

    /** 当月新登记缺陷数 */
    @Column(name = "total_count")
    private Integer totalCount;

    /** 当月已闭环（验收）数 */
    @Column(name = "closed_count")
    private Integer closedCount;

    /** 当月仍超期未闭环数 */
    @Column(name = "overdue_count")
    private Integer overdueCount;

    /** 限期未到且尚未闭环数（在期在建） */
    @Column(name = "open_count")
    private Integer openCount;

    /** 消缺率（%），存储过程内直接算好 */
    @Column(name = "fix_rate")
    private BigDecimal fixRate;
}
