package com.substation.ops.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 处理人「报完工」请求体：提交处理结果，缺陷进入待验收，不能由自己验收。
 */
@Getter
@Setter
@NoArgsConstructor
public class DefectProcessRequest {

    /** 处理人（消缺负责人/实际处理人） */
    @NotBlank(message = "处理人不能为空")
    private String person;

    /** 本次处理结果（做了什么、更换了什么、试验结论等） */
    @NotBlank(message = "请填写本次处理结果，再提交验收")
    private String result;
}
