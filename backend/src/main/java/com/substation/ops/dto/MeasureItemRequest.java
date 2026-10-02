package com.substation.ops.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 工作票安措项（编制入票时一行）。
 */
@Getter
@Setter
@NoArgsConstructor
public class MeasureItemRequest {

    /** GROUND_LINE/EARTH_SWITCH/BARRIER/SIGNBOARD/OTHER */
    @NotBlank(message = "安措类型不能为空")
    private String measureType;

    @NotNull(message = "所在间隔不能为空")
    private Long locationBayId;

    @NotBlank(message = "具体位置不能为空")
    private String locationDesc;

    @NotBlank(message = "要求动作不能为空")
    private String requiredAction;

    /** 是否需要拆除恢复，缺省按需要拆除处理（接地线/接地刀闸/遮栏/标示牌默认需要） */
    private Boolean needRestore;

    private Long installTicketId;

    private Integer installStepNo;

    private Long restoreTicketId;

    private Integer restoreStepNo;
}
