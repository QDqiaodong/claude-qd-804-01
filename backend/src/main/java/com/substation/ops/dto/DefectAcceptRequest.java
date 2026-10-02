package com.substation.ops.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 独立验收请求体：由与处理人不同的值班员填写验收结论。
 * 通过才闭环；驳回必须写明原因，缺陷退回处理中返工。
 */
@Getter
@Setter
@NoArgsConstructor
public class DefectAcceptRequest {

    /** 验收值班员（不得与本次处理人相同） */
    @NotBlank(message = "验收人不能为空")
    private String person;

    /** 验收结论：PASS 通过 / REJECT 驳回 */
    @NotBlank(message = "请选择验收结论（通过/驳回）")
    private String verdict;

    /** 验收意见；驳回时为必填的返工原因 */
    private String comment;
}
