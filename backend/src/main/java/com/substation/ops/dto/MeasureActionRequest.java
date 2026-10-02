package com.substation.ops.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 安措装设/拆除确认请求体。
 */
@Getter
@Setter
@NoArgsConstructor
public class MeasureActionRequest {

    @NotBlank(message = "执行人不能为空")
    private String person;

    /** 撤回许可、现场变更时必填的原因 */
    private String reason;
}
