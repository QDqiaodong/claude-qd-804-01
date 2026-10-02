package com.substation.ops.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 独立消缺验收请求：由不同于处理人的值班员填写结论。
 * pass=true 验收通过闭环；pass=false 驳回返工，rejectReason 必填。
 */
@Getter
@Setter
@NoArgsConstructor
public class DefectAcceptRequest {

    /** 验收人（不得与本轮处理人为同一人） */
    @NotBlank(message = "验收人不能为空")
    private String acceptor;

    /** true 通过 / false 驳回返工 */
    private boolean pass;

    /** 驳回原因（驳回时必填）；通过时可作为验收意见 */
    private String opinion;
}
