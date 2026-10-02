package com.substation.ops.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 派工请求：指定消缺负责人（处理人）。
 */
@Getter
@Setter
@NoArgsConstructor
public class DefectDispatchRequest {

    @NotBlank(message = "派工时必须指定消缺负责人")
    private String owner;
}
