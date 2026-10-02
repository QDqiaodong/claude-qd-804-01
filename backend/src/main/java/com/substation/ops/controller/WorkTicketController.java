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
import com.substation.ops.dto.MeasureActionRequest;
import com.substation.ops.dto.MeasureItemRequest;
import com.substation.ops.dto.SignRequest;
import com.substation.ops.dto.WithdrawRequest;
import com.substation.ops.dto.WorkTicketCreateRequest;
import com.substation.ops.service.WorkTicketService;

import jakarta.validation.Valid;

/**
 * 模块四：工作票（三审）+ 安全措施逐项执行闭环。
 */
@RestController
@RequestMapping("/api/work-tickets")
public class WorkTicketController {

    private final WorkTicketService workTicketService;

    public WorkTicketController(WorkTicketService workTicketService) {
        this.workTicketService = workTicketService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.success(workTicketService.listOfDay(date));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResponse.success(workTicketService.detail(id));
    }

    /** 【手写 JPQL】两票状态统计 */
    @GetMapping("/status-stats")
    public ApiResponse<List<Map<String, Object>>> statusStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.success("工作票票面状态统计", workTicketService.statusStats(date));
    }

    /** 票号由存储过程生成；安全措施逐项随票落表 */
    @PostMapping
    public ApiResponse<Map<String, Object>> create(@Valid @RequestBody WorkTicketCreateRequest request) {
        return ApiResponse.success("票号由存储过程生成并回写，安全措施已逐项入票", workTicketService.create(request));
    }

    /** 三审签字：ISSUE / PERMIT / TERMINATE（许可、终结内含双闸门核验） */
    @PostMapping("/{id}/sign")
    public ApiResponse<Map<String, Object>> sign(@PathVariable Long id, @Valid @RequestBody SignRequest request) {
        return ApiResponse.success("签字完成", workTicketService.sign(id, request.getStage(), request.getPerson()));
    }

    /** 安措逐项装设/合上确认 */
    @PostMapping("/{id}/measures/{measureId}/install")
    public ApiResponse<Map<String, Object>> installMeasure(@PathVariable Long id,
                                                           @PathVariable Long measureId,
                                                           @Valid @RequestBody MeasureActionRequest request) {
        return ApiResponse.success("装设确认完成", workTicketService.installMeasure(id, measureId, request.getPerson()));
    }

    /** 安措逐项拆除/取下确认（仅拆除恢复阶段） */
    @PostMapping("/{id}/measures/{measureId}/remove")
    public ApiResponse<Map<String, Object>> removeMeasure(@PathVariable Long id,
                                                          @PathVariable Long measureId,
                                                          @Valid @RequestBody MeasureActionRequest request) {
        return ApiResponse.success("拆除确认完成", workTicketService.removeMeasure(id, measureId, request.getPerson()));
    }

    /** 许可人通知拆除安措：作业中 → 安措拆除恢复中 */
    @PostMapping("/{id}/start-remove")
    public ApiResponse<Map<String, Object>> startRemove(@PathVariable Long id,
                                                        @Valid @RequestBody MeasureActionRequest request) {
        return ApiResponse.success("已通知拆除安措", workTicketService.startRemove(id, request.getPerson()));
    }

    /** 撤回许可：已确认痕迹保留，待拆除/已拆除项回到待执行 */
    @PostMapping("/{id}/withdraw")
    public ApiResponse<Map<String, Object>> withdraw(@PathVariable Long id,
                                                     @Valid @RequestBody WithdrawRequest request) {
        return ApiResponse.success("许可已撤回", workTicketService.withdrawPermit(id, request.getPerson(), request.getReason()));
    }

    /** 撤回许可后现场变更：原措施作废留痕，新版本回待执行 */
    @PostMapping("/{id}/measures/{measureId}/change")
    public ApiResponse<Map<String, Object>> changeMeasure(@PathVariable Long id,
                                                          @PathVariable Long measureId,
                                                          @Valid @RequestBody MeasureChangeBody body) {
        return ApiResponse.success("措施已变更（原确认痕迹保留）",
                workTicketService.changeMeasure(id, measureId, body.getMeasure(), body.getPerson(), body.getReason()));
    }

    /** 撤回许可后现场新增措施 */
    @PostMapping("/{id}/measures")
    public ApiResponse<Map<String, Object>> appendMeasure(@PathVariable Long id,
                                                         @Valid @RequestBody MeasureChangeBody body) {
        return ApiResponse.success("已新增措施，处于待执行",
                workTicketService.appendMeasure(id, body.getMeasure(), body.getPerson(), body.getReason()));
    }

    /** 作废工作票（历史痕迹保留） */
    @PostMapping("/{id}/cancel")
    public ApiResponse<Map<String, Object>> cancel(@PathVariable Long id,
                                                   @Valid @RequestBody WithdrawRequest request) {
        return ApiResponse.success("工作票已作废", workTicketService.cancel(id, request.getPerson(), request.getReason()));
    }

    /** 变更/新增安措请求体：人员、原因 + 新的措施内容 */
    @lombok.Getter
    @lombok.Setter
    public static class MeasureChangeBody {
        @jakarta.validation.constraints.NotBlank(message = "操作人不能为空")
        private String person;

        @jakarta.validation.constraints.NotBlank(message = "原因不能为空")
        private String reason;

        @jakarta.validation.constraints.NotNull(message = "措施内容不能为空")
        @Valid
        private MeasureItemRequest measure;
    }
}
