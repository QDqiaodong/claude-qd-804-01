package com.substation.ops.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.substation.ops.common.ApiResponse;
import com.substation.ops.dto.TimelineDay;
import com.substation.ops.service.DashboardService;

/**
 * 顶部时间轴数据源：每天的「两票」票量与当日概览。
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/timeline")
    public ApiResponse<List<TimelineDay>> timeline(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate start = from == null ? LocalDate.now().minusDays(3) : from;
        LocalDate end = to == null ? start.plusDays(6) : to;
        if (end.isBefore(start)) {
            end = start.plusDays(6);
        }
        return ApiResponse.success(dashboardService.timeline(start, end));
    }

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.success(dashboardService.overview(date));
    }
}
