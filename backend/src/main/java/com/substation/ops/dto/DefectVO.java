package com.substation.ops.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.substation.ops.domain.Defect;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 缺陷视图对象：补齐超期天数、闭环流程进度等前端要直接用但不落库的字段。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DefectVO {

    private Long id;
    private String defectNo;
    private Long bayId;
    private String bayName;
    private String voltageLevel;
    private Long equipmentId;
    private String equipmentNo;
    private String title;
    private String description;
    private String level;
    private String levelName;
    private LocalDateTime foundTime;
    private String reporter;
    private LocalDate dueDate;
    private String owner;
    private String status;
    private String statusName;
    private LocalDateTime closedTime;
    private Integer overdueDays;
    private String nextStage;
    private String nextStageName;

    public static DefectVO from(Defect d) {
        DefectVO vo = new DefectVO();
        vo.setId(d.getId());
        vo.setDefectNo(d.getDefectNo());
        vo.setBayId(d.getBayId());
        vo.setEquipmentId(d.getEquipmentId());
        vo.setTitle(d.getTitle());
        vo.setDescription(d.getDescription());
        vo.setLevel(d.getLevel());
        vo.setFoundTime(d.getFoundTime());
        vo.setReporter(d.getReporter());
        vo.setDueDate(d.getDueDate());
        vo.setOwner(d.getOwner());
        vo.setStatus(d.getStatus());
        vo.setClosedTime(d.getClosedTime());
        return vo;
    }
}
