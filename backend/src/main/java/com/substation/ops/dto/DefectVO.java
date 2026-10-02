package com.substation.ops.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.substation.ops.domain.Defect;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 缺陷视图对象：补齐超期天数、闭环流程进度、最近一次处理/验收信息与下一步可做什么。
 * 前端刷新后直接据此显示真实阶段与下一步，不靠前端猜状态。
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

    // ---------------- 最近一次处理（报完工） ----------------
    private String processResult;
    private String processPerson;
    private LocalDateTime processTime;

    // ---------------- 最近一次验收 ----------------
    private String acceptResult;
    private String acceptPerson;
    private LocalDateTime acceptTime;
    private Integer rejectCount;

    private LocalDateTime closedTime;
    private Integer overdueDays;

    /** 下一环节状态码（待验收/已验收等），无下一环节时为 null */
    private String nextStage;
    private String nextStageName;
    /** 下一步操作的人话指引（如「等待与处理人不同的值班员验收」） */
    private String nextActionText;

    // ---------------- 后端按真实阶段给出的可操作标志 ----------------
    /** 可派工（登记态） */
    private boolean canDispatch;
    /** 可开始处理（已派工） */
    private boolean canStartProcess;
    /** 可报完工提交验收（处理中，含驳回返工） */
    private boolean canSubmitResult;
    /** 可独立验收（待验收） */
    private boolean canAccept;
    /** 已闭环 */
    private boolean closed;

    /** 历次处理/验收轮次（驳回返工全过程留痕） */
    private List<DefectRoundVO> rounds;

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
        vo.setProcessPerson(d.getProcessPerson());
        vo.setProcessTime(d.getProcessTime());
        vo.setAcceptResult(d.getAcceptResult());
        vo.setAcceptPerson(d.getAcceptPerson());
        vo.setAcceptTime(d.getAcceptTime());
        vo.setRejectCount(d.getRejectCount() == null ? 0 : d.getRejectCount());
        vo.setClosedTime(d.getClosedTime());
        return vo;
    }
}
