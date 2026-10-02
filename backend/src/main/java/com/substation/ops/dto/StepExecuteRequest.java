package com.substation.ops.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 新增一步倒闸操作（已存在的步骤则在构建票临时id时一次性写入）。
 */
@Getter
@Setter
@NoArgsConstructor
public class StepExecuteRequest {

    @NotBlank(message = "执行人不能为空")
    private String person;
}
