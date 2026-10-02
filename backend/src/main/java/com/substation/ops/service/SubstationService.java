package com.substation.ops.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.substation.ops.common.Labels;
import com.substation.ops.domain.Bay;
import com.substation.ops.domain.Equipment;
import com.substation.ops.domain.EquipmentSummary;
import com.substation.ops.domain.Substation;
import com.substation.ops.dto.BayVO;
import com.substation.ops.repository.BayRepository;
import com.substation.ops.repository.DefectRepository;
import com.substation.ops.repository.EquipmentRepository;
import com.substation.ops.repository.EquipmentSummaryRepository;
import com.substation.ops.repository.SubstationRepository;
import com.substation.ops.repository.WorkTicketRepository;

/**
 * 模块一 / 二：变电站、间隔、设备台账。
 */
@Service
public class SubstationService {

    private final SubstationRepository substationRepository;
    private final BayRepository bayRepository;
    private final EquipmentRepository equipmentRepository;
    private final DefectRepository defectRepository;
    private final WorkTicketRepository workTicketRepository;
    private final EquipmentSummaryRepository equipmentSummaryRepository;

    public SubstationService(SubstationRepository substationRepository,
                             BayRepository bayRepository,
                             EquipmentRepository equipmentRepository,
                             DefectRepository defectRepository,
                             WorkTicketRepository workTicketRepository,
                             EquipmentSummaryRepository equipmentSummaryRepository) {
        this.substationRepository = substationRepository;
        this.bayRepository = bayRepository;
        this.equipmentRepository = equipmentRepository;
        this.defectRepository = defectRepository;
        this.workTicketRepository = workTicketRepository;
        this.equipmentSummaryRepository = equipmentSummaryRepository;
    }

    public List<Substation> listSubstations() {
        return substationRepository.findAll();
    }

    /**
     * 间隔列表（平面示意图用），顺带带出所属站名、设备台数、未闭环缺陷数、当日工作票数。
     */
    @Transactional(readOnly = true)
    public List<BayVO> listBays(Long substationId, LocalDate date) {
        List<Bay> bays = substationId == null
                ? bayRepository.findAll()
                : bayRepository.findBySubstationIdOrderBySortNoAsc(substationId);

        Map<Long, String> stationNames = new HashMap<>();
        for (Substation s : substationRepository.findAll()) {
            stationNames.put(s.getId(), s.getName());
        }

        List<BayVO> result = new ArrayList<>();
        for (Bay b : bays) {
            BayVO vo = BayVO.from(b);
            vo.setSubstationName(stationNames.getOrDefault(b.getSubstationId(), "未知站"));
            vo.setBayTypeName(Labels.bayType(b.getBayType()));
            vo.setStatusName(Labels.bayStatus(b.getStatus()));
            vo.setEquipmentCount(equipmentRepository.findByBayIdOrderByIdAsc(b.getId()).stream().count());
            vo.setOpenDefectCount(defectRepository.countByBayIdAndStatusNot(b.getId(), "ACCEPTED"));
            vo.setWorkTicketCount(date == null ? 0L : countByBayAndDate(b.getId(), date));
            result.add(vo);
        }
        return result;
    }

    private long countByBayAndDate(Long bayId, LocalDate date) {
        return workTicketRepository.findByIssueDateOrderByIdAsc(date).stream()
                .filter(t -> bayId.equals(t.getBayId()))
                .count();
    }

    @Transactional(readOnly = true)
    public Bay getBay(Long id) {
        return bayRepository.findById(id).orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<Equipment> listEquipment(Long bayId) {
        return equipmentRepository.findByBayIdOrderByIdAsc(bayId);
    }

    @Transactional(readOnly = true)
    public List<Equipment> listAllEquipment() {
        return equipmentRepository.findAll();
    }

    /**
     * 【存储过程】按电压等级汇总设备台数与待检数。
     * 不用 readOnly=true：MySQL 的 CALL 语句会被 Connector/J 判为可能改数据而拒绝执行。
     */
    @Transactional
    public List<EquipmentSummary> equipmentSummaryByVoltage(Long substationId) {
        return equipmentSummaryRepository.summaryByVoltage(substationId == null ? 0L : substationId);
    }
}
