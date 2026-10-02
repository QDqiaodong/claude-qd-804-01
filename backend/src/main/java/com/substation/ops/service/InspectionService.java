package com.substation.ops.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.substation.ops.common.BizException;
import com.substation.ops.common.Labels;
import com.substation.ops.domain.Bay;
import com.substation.ops.domain.Defect;
import com.substation.ops.domain.DefectSummary;
import com.substation.ops.domain.Equipment;
import com.substation.ops.domain.InspectionPlan;
import com.substation.ops.domain.InspectionRecord;
import com.substation.ops.dto.DefectCreateRequest;
import com.substation.ops.dto.DefectVO;
import com.substation.ops.dto.OverdueDefectRow;
import com.substation.ops.repository.BayRepository;
import com.substation.ops.repository.DefectRepository;
import com.substation.ops.repository.DefectSummaryRepository;
import com.substation.ops.repository.EquipmentRepository;
import com.substation.ops.repository.InspectionPlanRepository;
import com.substation.ops.repository.InspectionRecordRepository;

/**
 * 模块三：巡检与缺陷。巡检由记录，缺陷走「登记 → 派工 → 处理 → 验收」闭环。
 */
@Service
public class InspectionService {

    private final InspectionPlanRepository planRepository;
    private final InspectionRecordRepository recordRepository;
    private final DefectRepository defectRepository;
    private final DefectSummaryRepository defectSummaryRepository;
    private final BayRepository bayRepository;
    private final EquipmentRepository equipmentRepository;

    public InspectionService(InspectionPlanRepository planRepository,
                             InspectionRecordRepository recordRepository,
                             DefectRepository defectRepository,
                             DefectSummaryRepository defectSummaryRepository,
                             BayRepository bayRepository,
                             EquipmentRepository equipmentRepository) {
        this.planRepository = planRepository;
        this.recordRepository = recordRepository;
        this.defectRepository = defectRepository;
        this.defectSummaryRepository = defectSummaryRepository;
        this.bayRepository = bayRepository;
        this.equipmentRepository = equipmentRepository;
    }

    @Transactional(readOnly = true)
    public List<InspectionPlan> plansOfDay(LocalDate date) {
        LocalDate day = date == null ? LocalDate.now() : date;
        return planRepository.findByPlanDateOrderByShiftAscIdAsc(day);
    }

    @Transactional(readOnly = true)
    public List<InspectionRecord> recordsOfDay(LocalDate date) {
        LocalDate day = date == null ? LocalDate.now() : date;
        return recordRepository.findByDay(day.atStartOfDay(), day.plusDays(1).atStartOfDay());
    }

    @Transactional(readOnly = true)
    public List<DefectVO> defects(Long bayId) {
        List<Defect> defects = bayId == null ? defectRepository.findAll() : defectRepository.findByBayIdOrderByDueDateAsc(bayId);
        return enrich(defects);
    }

    /**
     * 【手写 JPQL】缺陷超期排行：按超期天数从多到少排序。
     */
    @Transactional(readOnly = true)
    public List<OverdueDefectRow> overdueRanking() {
        LocalDate today = LocalDate.now();
        List<OverdueDefectRow> rows = defectRepository.rankOverdue(Defect.STATUS_ACCEPTED, today);
        for (OverdueDefectRow row : rows) {
            row.setOverdueDays((int) ChronoUnit.DAYS.between(row.getDueDate(), today));
        }
        rows.sort((a, b) -> Integer.compare(b.getOverdueDays(), a.getOverdueDays()));
        return rows;
    }

    /**
     * 【存储过程】某变电站当月缺陷数与消缺率。
     * 注意不能用 readOnly=true：MySQL 的 CALL 语句会被 Connector/J 判为可能改数据而拒绝执行。
     */
    @Transactional
    public List<DefectSummary> defectSummary(Long substationId, String month) {
        return defectSummaryRepository.summaryByMonth(substationId == null ? 0L : substationId, month);
    }

    /**
     * 登记缺陷：按等级自动算出限期消缺日期（紧急 1 天 / 重大 7 天 / 一般 30 天）。
     */
    @Transactional
    public DefectVO register(DefectCreateRequest request) {
        Bay bay = bayRepository.findById(request.getBayId())
                .orElseThrow(() -> new BizException("所选间隔不存在，无法登记缺陷"));

        String level = request.getLevel();
        if (!level.equals(Defect.LEVEL_URGENT) && !level.equals(Defect.LEVEL_MAJOR) && !level.equals(Defect.LEVEL_MINOR)) {
            throw new BizException("缺陷等级只能是 紧急 / 重大 / 一般");
        }

        Equipment equipment = null;
        if (request.getEquipmentId() != null) {
            equipment = equipmentRepository.findById(request.getEquipmentId()).orElse(null);
        }

        Defect defect = new Defect();
        defect.setBayId(bay.getId());
        defect.setEquipmentId(equipment == null ? null : equipment.getId());
        defect.setTitle(request.getTitle());
        defect.setDescription(request.getDescription());
        defect.setLevel(level);
        defect.setReporter(request.getReporter());
        defect.setFoundTime(LocalDateTime.now());
        defect.setDueDate(LocalDate.now().plusDays(dueDays(level)));
        defect.setStatus(Defect.STATUS_REGISTERED);
        defect.setDefectNo("QX" + System.currentTimeMillis() % 1000000L);

        Defect saved = defectRepository.save(defect);
        return enrich(List.of(saved)).get(0);
    }

    private int dueDays(String level) {
        if (Defect.LEVEL_URGENT.equals(level)) {
            return Defect.DUE_DAYS_URGENT;
        }
        if (Defect.LEVEL_MAJOR.equals(level)) {
            return Defect.DUE_DAYS_MAJOR;
        }
        return Defect.DUE_DAYS_MINOR;
    }

    /**
     * 消缺推进：登记 → 派工 → 处理 → 验收。
     */
    @Transactional
    public DefectVO advance(Long id, String owner) {
        Defect defect = defectRepository.findById(id)
                .orElseThrow(() -> new BizException("缺陷单不存在"));

        switch (defect.getStatus()) {
            case Defect.STATUS_REGISTERED -> {
                defect.setStatus(Defect.STATUS_DISPATCHED);
                if (owner != null && !owner.isBlank()) {
                    defect.setOwner(owner);
                }
            }
            case Defect.STATUS_DISPATCHED -> {
                if (defect.getOwner() == null || defect.getOwner().isBlank()) {
                    if (owner == null || owner.isBlank()) {
                        throw new BizException("派工时必须指定消缺负责人，才能进入处理环节");
                    }
                    defect.setOwner(owner);
                }
                defect.setStatus(Defect.STATUS_PROCESSING);
            }
            case Defect.STATUS_PROCESSING -> {
                defect.setStatus(Defect.STATUS_ACCEPTED);
                defect.setClosedTime(LocalDateTime.now());
            }
            default -> throw new BizException("缺陷单已验收闭环，无需再推进");
        }

        Defect saved = defectRepository.save(defect);
        return enrich(List.of(saved)).get(0);
    }

    private List<DefectVO> enrich(List<Defect> defects) {
        Map<Long, Bay> bayMap = new HashMap<>();
        for (Bay b : bayRepository.findAll()) {
            bayMap.put(b.getId(), b);
        }
        Map<Long, Equipment> equipMap = new HashMap<>();
        for (Equipment e : equipmentRepository.findAll()) {
            equipMap.put(e.getId(), e);
        }

        List<DefectVO> vos = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (Defect d : defects) {
            DefectVO vo = DefectVO.from(d);
            Bay bay = bayMap.get(d.getBayId());
            Equipment equipment = d.getEquipmentId() == null ? null : equipMap.get(d.getEquipmentId());
            vo.setBayName(bay == null ? "未知间隔" : bay.getName());
            vo.setVoltageLevel(bay == null ? "" : bay.getVoltageLevel());
            vo.setEquipmentNo(equipment == null ? "" : equipment.getEquipmentNo());
            vo.setLevelName(Labels.defectLevel(d.getLevel()));
            vo.setStatusName(Labels.defectStatus(d.getStatus()));
            vo.setOverdueDays(d.getDueDate() == null || !today.isAfter(d.getDueDate())
                    ? 0
                    : (int) ChronoUnit.DAYS.between(d.getDueDate(), today));
            vo.setNextStage(nextStage(d.getStatus()));
            vo.setNextStageName(vo.getNextStage() == null ? "" : Labels.defectStatus(vo.getNextStage()));
            vos.add(vo);
        }
        return vos;
    }

    private String nextStage(String status) {
        if (Defect.STATUS_REGISTERED.equals(status)) {
            return Defect.STATUS_DISPATCHED;
        }
        if (Defect.STATUS_DISPATCHED.equals(status)) {
            return Defect.STATUS_PROCESSING;
        }
        if (Defect.STATUS_PROCESSING.equals(status)) {
            return Defect.STATUS_ACCEPTED;
        }
        return null;
    }
}
