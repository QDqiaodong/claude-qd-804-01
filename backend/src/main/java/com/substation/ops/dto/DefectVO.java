package com.substation.ops.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
    private String processResult;
    private String processedBy;
    private LocalDateTime processedTime;
    private Integer reworkCount;
    private String acceptedBy;
    private LocalDateTime closedTime;
    private Integer overdueDays;
    private String nextStage;
    private String nextStageName;
    /** 下一步提示语（刷新/重进后据此显示真实阶段和下一步可做什么） */
    private String waitingText;

    /** 当前阶段允许的操作（页面按钮严格按后端口径点亮/禁用，不靠前端猜状态文案） */
    private boolean canDispatch;
    private boolean canStartProcess;
    private boolean canSubmitResult;
    private boolean canAccept;
    /** 当前是否处于待验收环节（页面应展示验收区而不是处理区） */
    private boolean pendingAcceptance;
    /** 是否已闭环（验收通过终态） */
    private boolean closed;

    /** 历轮处理/验收记录（只增不改；驳回返工的处理人、结果、验收人、原因都保留） */
    private List<DefectAcceptanceVO> acceptances = new ArrayList<>();

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
        vo.setProcessResult(d.getProcessResult());
        vo.setProcessedBy(d.getProcessedBy());
        vo.setProcessedTime(d.getProcessedTime());
        vo.setReworkCount(d.getReworkCount() == null ? 0 : d.getReworkCount());
        vo.setAcceptedBy(d.getAcceptedBy());
        vo.setClosedTime(d.getClosedTime());
        return vo;
    }
}
