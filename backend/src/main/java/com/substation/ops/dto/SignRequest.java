package com.substation.ops.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 三审签字请求体：stage = ISSUE / PERMIT / TERMINATE。
 */
@Getter
@Setter
@NoArgsConstructor
public class SignRequest {

    @NotBlank(message = "签字环节不能为空")
    private String stage;

    @NotBlank(message = "签字人不能为空")
    private String person;
}
