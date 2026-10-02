package com.substation.ops.dto;

import java.time.LocalDateTime;

import com.substation.ops.domain.DefectAcceptance;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 一条处理/验收记录视图：驳回返工时历史各轮的处理人、处理结果、验收人与原因全部保留可查。
 */
@Getter
@Setter
@NoArgsConstructor
public class DefectAcceptanceVO {

    private Long id;
    private Integer roundNo;
    private String processedBy;
    private String processResult;
    private LocalDateTime submittedTime;
    private String acceptedBy;
    /** PASS / REJECTED / null（待验收中） */
    private String result;
    private String resultName;
    private String acceptOpinion;
    private LocalDateTime acceptedTime;
    private boolean pending;

    public static DefectAcceptanceVO from(DefectAcceptance a) {
        DefectAcceptanceVO vo = new DefectAcceptanceVO();
        vo.setId(a.getId());
        vo.setRoundNo(a.getRoundNo());
        vo.setProcessedBy(a.getProcessedBy());
        vo.setProcessResult(a.getProcessResult());
        vo.setSubmittedTime(a.getSubmittedTime());
        vo.setAcceptedBy(a.getAcceptedBy());
        vo.setResult(a.getResult());
        vo.setAcceptOpinion(a.getAcceptOpinion());
        vo.setAcceptedTime(a.getAcceptedTime());
        vo.setPending(a.getResult() == null);
        return vo;
    }
}
