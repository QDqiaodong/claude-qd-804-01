package com.substation.ops.dto;

import java.time.LocalDateTime;

import com.substation.ops.domain.DefectClosureRound;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 缺陷消缺验收一轮的视图对象：处理人报完工 → 另一值班员验收（通过/驳回）。
 */
@Getter
@Setter
@NoArgsConstructor
public class DefectRoundVO {

    private Long id;
    private Integer roundNo;
    private String processResult;
    private String processPerson;
    private LocalDateTime processTime;
    private String acceptVerdict;
    private String acceptVerdictName;
    private String acceptComment;
    private String acceptPerson;
    private LocalDateTime acceptTime;
    private String status;
    private String statusName;

    public static DefectRoundVO from(DefectClosureRound r,
                                     String verdictName, String statusName) {
        DefectRoundVO vo = new DefectRoundVO();
        vo.setId(r.getId());
        vo.setRoundNo(r.getRoundNo());
        vo.setProcessResult(r.getProcessResult());
        vo.setProcessPerson(r.getProcessPerson());
        vo.setProcessTime(r.getProcessTime());
        vo.setAcceptVerdict(r.getAcceptVerdict());
        vo.setAcceptVerdictName(verdictName);
        vo.setAcceptComment(r.getAcceptComment());
        vo.setAcceptPerson(r.getAcceptPerson());
        vo.setAcceptTime(r.getAcceptTime());
        vo.setStatus(r.getStatus());
        vo.setStatusName(statusName);
        return vo;
    }
}
