package com.substation.ops.dto;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 顶部时间轴上的一天：当天工作票 / 操作票的票量徽章。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TimelineDay {

    private LocalDate date;
    private Long workTicketCount;
    private Long operationTicketCount;

    public Long getTotal() {
        return workTicketCount + operationTicketCount;
    }
}
