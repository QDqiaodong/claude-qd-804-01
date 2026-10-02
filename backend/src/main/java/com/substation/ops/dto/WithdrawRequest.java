package com.substation.ops.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 撤回许可请求体。
 */
@Getter
@Setter
@NoArgsConstructor
public class WithdrawRequest {

    @NotBlank(message = "撤回操作人不能为空")
    private String person;

    @NotBlank(message = "撤回原因不能为空")
    private String reason;
}
