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
import com.substation.ops.dto.DefectAcceptRequest;
import com.substation.ops.dto.DefectCreateRequest;
import com.substation.ops.dto.DefectDispatchRequest;
import com.substation.ops.dto.DefectSubmitResultRequest;
import com.substation.ops.dto.DefectVO;
import com.substation.ops.dto.OverdueDefectRow;
import com.substation.ops.service.InspectionService;

import jakarta.validation.Valid;

/**
 * 模块三：巡检记录与缺陷闭环。
 *
 * 闭环链路：登记 → 派工（指定处理人）→ 开工处理 → 处理人提交结果【待验收】
 *          → 另一名值班员独立验收：通过闭环 / 驳回退回处理中返工。
 * 处理与验收是两个独立接口、两类操作人，不能再一步从「处理中」直达「已验收」。
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

    /** 【手写 JPQL】缺陷超期排行（待验收、驳回返工仍在榜） */
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

    /** 环节一：派工，指定消缺负责人（处理人） */
    @PostMapping("/{id}/dispatch")
    public ApiResponse<DefectVO> dispatch(@PathVariable Long id,
                                          @Valid @RequestBody DefectDispatchRequest request) {
        return ApiResponse.success("已派工", inspectionService.dispatch(id, request));
    }

    /** 环节二：已派工 → 处理中 */
    @PostMapping("/{id}/start-process")
    public ApiResponse<DefectVO> startProcess(@PathVariable Long id) {
        return ApiResponse.success("已开工处理", inspectionService.startProcess(id));
    }

    /**
     * 环节三（处理人）：提交处理结果报完工，缺陷进入「待验收」。
     * 不闭环、不解除超期阻断，等待另一名值班员验收。
     */
    @PostMapping("/{id}/submit-result")
    public ApiResponse<DefectVO> submitResult(@PathVariable Long id,
                                              @Valid @RequestBody DefectSubmitResultRequest request) {
        return ApiResponse.success("处理结果已提交，进入待验收", inspectionService.submitResult(id, request));
    }

    /**
     * 环节四（验收人，必须不同于处理人）：填写验收结论。
     * pass=true 验收通过闭环；pass=false 驳回，退回处理中返工并保留全部处理/验收痕迹。
     */
    @PostMapping("/{id}/accept")
    public ApiResponse<DefectVO> accept(@PathVariable Long id,
                                        @Valid @RequestBody DefectAcceptRequest request) {
        String msg = request.isPass() ? "验收通过，缺陷闭环" : "已驳回，缺陷退回处理中返工";
        return ApiResponse.success(msg, inspectionService.accept(id, request));
    }
}
