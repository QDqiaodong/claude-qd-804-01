package com.substation.ops.dto;

import com.substation.ops.domain.Bay;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 间隔视图对象：平面示意图格子要用到的全部字段。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BayVO {

    private Long id;
    private Long substationId;
    private String substationName;
    private String code;
    private String name;
    private String bayType;
    private String bayTypeName;
    private String voltageLevel;
    private String status;
    private String statusName;
    private Integer sortNo;
    private String remark;
    private Long equipmentCount;
    private Long openDefectCount;
    private Long workTicketCount;

    public static BayVO from(Bay b) {
        BayVO vo = new BayVO();
        vo.setId(b.getId());
        vo.setSubstationId(b.getSubstationId());
        vo.setCode(b.getCode());
        vo.setName(b.getName());
        vo.setBayType(b.getBayType());
        vo.setVoltageLevel(b.getVoltageLevel());
        vo.setStatus(b.getStatus());
        vo.setSortNo(b.getSortNo());
        vo.setRemark(b.getRemark());
        return vo;
    }
}
