package com.substation.ops.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 两票状态统计一行（手写 JPQL group by 投影）。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TicketStatusCount {

    private String status;
    private Long count;
}
