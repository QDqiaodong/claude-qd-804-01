package com.substation.ops.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 登记缺陷的请求体（间隔平面示意图上直接登记）。
 */
@Getter
@Setter
@NoArgsConstructor
public class DefectCreateRequest {

    @NotNull(message = "间隔不能为空")
    private Long bayId;

    private Long equipmentId;

    @NotBlank(message = "缺陷现象不能为空")
    private String title;

    private String description;

    @NotBlank(message = "缺陷等级不能为空")
    private String level;

    @NotBlank(message = "登记人不能为空")
    private String reporter;
}
