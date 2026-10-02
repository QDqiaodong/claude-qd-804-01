package com.substation.ops.dto;

import java.time.LocalDate;

import com.substation.ops.domain.Defect;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 缺陷超期排行一行。由 DefectRepository 的手写 JPQL 直接投影出来。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OverdueDefectRow {

    private Long id;
    private String defectNo;
    private String title;
    private String level;
    private Long bayId;
    private LocalDate dueDate;
    private String status;
    /** 当前环节中文名（待验收/处理中等），刷新后展示真实阶段 */
    private String statusName;
    private Integer overdueDays;

    public OverdueDefectRow(Long id, String defectNo, String title, String level, Long bayId,
                            LocalDate dueDate, String status, Integer overdueDays) {
        this.id = id;
        this.defectNo = defectNo;
        this.title = title;
        this.level = level;
        this.bayId = bayId;
        this.dueDate = dueDate;
        this.status = status;
        this.overdueDays = overdueDays;
    }

    public static OverdueDefectRow of(Defect d, Integer overdueDays) {
        return new OverdueDefectRow(d.getId(), d.getDefectNo(), d.getTitle(), d.getLevel(), d.getBayId(),
                d.getDueDate(), d.getStatus(), overdueDays);
    }
}
