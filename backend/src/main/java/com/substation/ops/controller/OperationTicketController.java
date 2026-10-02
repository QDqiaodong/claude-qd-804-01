package com.substation.ops.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.substation.ops.common.ApiResponse;
import com.substation.ops.dto.StepExecuteRequest;
import com.substation.ops.dto.StepFailRequest;
import com.substation.ops.service.OperationTicketService;

import jakarta.validation.Valid;

/**
 * 模块五：操作票与倒闸操作步骤（顺序执行、失败登记）。
 */
@RestController
@RequestMapping("/api/operation-tickets")
public class OperationTicketController {

    private final OperationTicketService operationTicketService;

    public OperationTicketController(OperationTicketService operationTicketService) {
        this.operationTicketService = operationTicketService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.success(operationTicketService.listOfDay(date));
    }

    /** 全部操作票（含步骤状态）：工作票安措绑定装设/恢复步骤时选择用，不限日期 */
    @GetMapping("/all-brief")
    public ApiResponse<List<Map<String, Object>>> listAll() {
        return ApiResponse.success(operationTicketService.listAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResponse.success(operationTicketService.detail(id));
    }

    /** 按步骤序号执行，不得跳步；并发执行同一步只生效一次 */
    @PostMapping("/{id}/steps/{stepNo}/execute")
    public ApiResponse<Map<String, Object>> executeStep(@PathVariable Long id,
                                                        @PathVariable Integer stepNo,
                                                        @Valid @RequestBody(required = false) StepExecuteRequest request) {
        String person = request == null ? null : request.getPerson();
        return ApiResponse.success("第 " + stepNo + " 步已执行", operationTicketService.executeStep(id, stepNo, person));
    }

    /** 登记某步操作失败：停在原步，不得跳越，可重试 */
    @PostMapping("/{id}/steps/{stepNo}/fail")
    public ApiResponse<Map<String, Object>> failStep(@PathVariable Long id,
                                                     @PathVariable Integer stepNo,
                                                     @Valid @RequestBody StepFailRequest request) {
        return ApiResponse.success("第 " + stepNo + " 步已登记操作失败",
                operationTicketService.failStep(id, stepNo, request.getPerson(), request.getReason()));
    }
}
