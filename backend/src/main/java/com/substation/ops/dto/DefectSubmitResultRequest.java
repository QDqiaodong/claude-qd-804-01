package com.substation.ops.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 处理人提交处理结果（报完工）请求。
 * 提交后缺陷进入「待验收」，由另一名值班员独立验收，处理人不能替自己验收。
 */
@Getter
@Setter
@NoArgsConstructor
public class DefectSubmitResultRequest {

    /** 提交人（须为该缺陷的消缺负责人） */
    @NotBlank(message = "处理结果提交人不能为空")
    private String processor;

    @NotBlank(message = "请填写本次处理情况/消缺结果，再提交验收")
    private String processResult;
}
