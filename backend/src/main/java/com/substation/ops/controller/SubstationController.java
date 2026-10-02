package com.substation.ops.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.substation.ops.common.ApiResponse;
import com.substation.ops.domain.EquipmentSummary;
import com.substation.ops.domain.Substation;
import com.substation.ops.dto.BayVO;
import com.substation.ops.service.SubstationService;

/**
 * 模块一 / 二：变电站、间隔、设备台账。
 */
@RestController
@RequestMapping("/api/substations")
public class SubstationController {

    private final SubstationService substationService;

    public SubstationController(SubstationService substationService) {
        this.substationService = substationService;
    }

    @GetMapping
    public ApiResponse<List<Substation>> listSubstations() {
        return ApiResponse.success(substationService.listSubstations());
    }

    @GetMapping("/bays")
    public ApiResponse<List<BayVO>> listBays(
            @RequestParam(required = false) Long substationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.success(substationService.listBays(substationId, date));
    }

    @GetMapping("/bays/{id}")
    public ApiResponse<?> getBay(@PathVariable Long id) {
        return ApiResponse.success(substationService.getBay(id));
    }

    @GetMapping("/equipments")
    public ApiResponse<List<com.substation.ops.domain.Equipment>> listEquipment(
            @RequestParam(required = false) Long bayId) {
        return ApiResponse.success(bayId == null
                ? substationService.listAllEquipment()
                : substationService.listEquipment(bayId));
    }

    /**
     * 【走存储过程】sp_equipment_summary_by_voltage：按电压等级汇总设备台数与待检数。
     */
    @GetMapping("/equipment-summary")
    public ApiResponse<List<EquipmentSummary>> equipmentSummary(
            @RequestParam(required = false) Long substationId) {
        return ApiResponse.success("存储过程 sp_equipment_summary_by_voltage",
                substationService.equipmentSummaryByVoltage(substationId));
    }
}
