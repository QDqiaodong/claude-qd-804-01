package com.substation.ops.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.substation.ops.common.ApiResponse;
import com.substation.ops.domain.DefectSummary;
import com.substation.ops.dto.DefectCreateRequest;
import com.substation.ops.dto.DefectVO;
import com.substation.ops.dto.OverdueDefectRow;
import com.substation.ops.service.InspectionService;

import jakarta.validation.Valid;

/**
 * 模块三：巡检记录与缺陷闭环。
 */
@RestController
@RequestMapping("/api/defects")
public class InspectionController {

    private final InspectionService inspectionService;

    public InspectionController(InspectionService inspectionService) {
        this.inspectionService = inspectionService;
    }

    @GetMapping("/inspection-plans")
    public ApiResponse<List<com.substation.ops.domain.InspectionPlan>> plans(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.success(inspectionService.plansOfDay(date));
    }

    @GetMapping("/inspection-records")
    public ApiResponse<List<com.substation.ops.domain.InspectionRecord>> records(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.success(inspectionService.recordsOfDay(date));
    }

    @GetMapping
    public ApiResponse<List<DefectVO>> list(@RequestParam(required = false) Long bayId) {
        return ApiResponse.success(inspectionService.defects(bayId));
    }

    /** 【手写 JPQL】缺陷超期排行 */
    @GetMapping("/overdue-ranking")
    public ApiResponse<List<OverdueDefectRow>> overdueRanking() {
        return ApiResponse.success("缺陷超期排行（按超期天数降序）", inspectionService.overdueRanking());
    }

    /** 【走存储过程】sp_defect_summary：当月缺陷数与消缺率 */
    @GetMapping("/summary")
    public ApiResponse<List<DefectSummary>> summary(
            @RequestParam(required = false) Long substationId,
            @RequestParam(required = false) String month) {
        return ApiResponse.success("存储过程 sp_defect_summary", inspectionService.defectSummary(substationId, month));
    }

    @PostMapping
    public ApiResponse<DefectVO> register(@Valid @RequestBody DefectCreateRequest request) {
        return ApiResponse.success("缺陷已登记", inspectionService.register(request));
    }

    /** 消缺推进：登记 → 派工 → 处理 → 验收 */
    @PostMapping("/{id}/advance")
    public ApiResponse<DefectVO> advance(@PathVariable Long id,
                                         @RequestParam(required = false) String owner) {
        return ApiResponse.success("消缺环节已推进", inspectionService.advance(id, owner));
    }
}
