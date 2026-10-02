package com.substation.ops.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 操作步骤执行失败登记请求体。失败后停在原步，不得跳步，可重新执行。
 */
@Getter
@Setter
@NoArgsConstructor
public class StepFailRequest {

    @NotBlank(message = "登记人不能为空")
    private String person;

    @NotBlank(message = "失败原因不能为空")
    private String reason;
}
