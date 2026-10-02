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
import com.substation.ops.domain.DefectAcceptance;
import com.substation.ops.domain.DefectSummary;
import com.substation.ops.domain.Equipment;
import com.substation.ops.domain.InspectionPlan;
import com.substation.ops.domain.InspectionRecord;
import com.substation.ops.dto.DefectAcceptRequest;
import com.substation.ops.dto.DefectAcceptanceVO;
import com.substation.ops.dto.DefectCreateRequest;
import com.substation.ops.dto.DefectDispatchRequest;
import com.substation.ops.dto.DefectSubmitResultRequest;
import com.substation.ops.dto.DefectVO;
import com.substation.ops.dto.OverdueDefectRow;
import com.substation.ops.repository.BayRepository;
import com.substation.ops.repository.DefectAcceptanceRepository;
import com.substation.ops.repository.DefectRepository;
import com.substation.ops.repository.DefectSummaryRepository;
import com.substation.ops.repository.EquipmentRepository;
import com.substation.ops.repository.InspectionPlanRepository;
import com.substation.ops.repository.InspectionRecordRepository;

/**
 * 模块三：巡检与缺陷。巡检由记录，缺陷走
 * 「登记 → 派工 → 处理 →（提交结果）待验收 →（独立验收通过）闭环」。
 *
 * 职责分离：处理人（消缺负责人）只能报完工提交结果进入待验收，
 * 验收必须由另一名值班员填写结论：通过才闭环（ACCEPTED，解除超期阻断），
 * 驳回则退回处理中返工，本次处理人、处理结果、验收人、驳回原因全部留痕。
 *
 * 所有推进动作先取 defect 行级悲观锁并在锁内重检状态：
 * 重复提交、两人同时验收同一缺陷都只能生效一次，后提交者看到最新阶段。
 */
@Service
public class InspectionService {

    private final InspectionPlanRepository planRepository;
    private final InspectionRecordRepository recordRepository;
    private final DefectRepository defectRepository;
    private final DefectAcceptanceRepository acceptanceRepository;
    private final DefectSummaryRepository defectSummaryRepository;
    private final BayRepository bayRepository;
    private final EquipmentRepository equipmentRepository;

    public InspectionService(InspectionPlanRepository planRepository,
                             InspectionRecordRepository recordRepository,
                             DefectRepository defectRepository,
                             DefectAcceptanceRepository acceptanceRepository,
                             DefectSummaryRepository defectSummaryRepository,
                             BayRepository bayRepository,
                             EquipmentRepository equipmentRepository) {
        this.planRepository = planRepository;
        this.recordRepository = recordRepository;
        this.defectRepository = defectRepository;
        this.acceptanceRepository = acceptanceRepository;
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
     * 待验收/驳回返工都属于未闭环，仍在排行中，不会因「报完工」就消失。
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
        defect.setReworkCount(0);
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

    // ============================================================
    // 消缺闭环推进（每一步独立接口 + 行级悲观锁 + 锁内状态重检）
    // ============================================================

    /** 第一步推进：已登记 → 已派工（指定消缺负责人/处理人） */
    @Transactional
    public DefectVO dispatch(Long id, DefectDispatchRequest request) {
        Defect defect = lockDefect(id);
        if (Defect.STATUS_ACCEPTED.equals(defect.getStatus())) {
            throw new BizException("缺陷单已验收闭环，无需再派工");
        }
        if (!Defect.STATUS_REGISTERED.equals(defect.getStatus())) {
            throw new BizException("缺陷当前为「" + Labels.defectStatus(defect.getStatus())
                    + "」，不能重复派工（处理人：" + defOwner(defect) + "）");
        }
        String owner = request == null ? null : request.getOwner();
        if (owner == null || owner.isBlank()) {
            throw new BizException("派工时必须指定消缺负责人，才能进入处理环节");
        }
        defect.setOwner(owner.trim());
        defect.setStatus(Defect.STATUS_DISPATCHED);
        Defect saved = defectRepository.save(defect);
        return enrich(List.of(saved)).get(0);
    }

    /** 第二步推进：已派工 → 处理中 */
    @Transactional
    public DefectVO startProcess(Long id) {
        Defect defect = lockDefect(id);
        if (Defect.STATUS_ACCEPTED.equals(defect.getStatus())) {
            throw new BizException("缺陷单已验收闭环，无需再开工处理");
        }
        if (Defect.STATUS_PROCESSING.equals(defect.getStatus())) {
            throw new BizException("缺陷已在处理中，处理人完成后请提交处理结果进入待验收");
        }
        if (Defect.STATUS_PENDING_ACCEPTANCE.equals(defect.getStatus())) {
            throw new BizException("处理结果已提交，当前为待验收，请等待验收人结论，不能重复开工");
        }
        if (!Defect.STATUS_DISPATCHED.equals(defect.getStatus())) {
            throw new BizException("缺陷尚未派工，不能直接进入处理环节");
        }
        defect.setStatus(Defect.STATUS_PROCESSING);
        Defect saved = defectRepository.save(defect);
        return enrich(List.of(saved)).get(0);
    }

    /**
     * 第三步（处理人侧）：处理中 / 驳回返工 → 提交处理结果 → 待验收。
     * 只能由消缺负责人本人提交；重复提交只生效一次。
     */
    @Transactional
    public DefectVO submitResult(Long id, DefectSubmitResultRequest request) {
        Defect defect = lockDefect(id);
        if (request == null || request.getProcessor() == null || request.getProcessor().isBlank()) {
            throw new BizException("处理结果提交人不能为空");
        }
        if (request.getProcessResult() == null || request.getProcessResult().isBlank()) {
            throw new BizException("请填写本次处理情况/消缺结果，再提交验收");
        }
        String processor = request.getProcessor().trim();
        if (defect.getOwner() == null || defect.getOwner().isBlank()) {
            throw new BizException("缺陷尚未派工，不能提交处理结果");
        }
        if (!defect.getOwner().trim().equals(processor)) {
            throw new BizException("只有消缺负责人【" + defect.getOwner()
                    + "】本人才能提交处理结果；提交后须由其他值班员验收，不能自行验收");
        }

        switch (defect.getStatus()) {
            case Defect.STATUS_REGISTERED, Defect.STATUS_DISPATCHED -> throw new BizException(
                    "缺陷尚未进入处理环节（当前为「" + Labels.defectStatus(defect.getStatus()) + "」），不能提前提交处理结果");
            case Defect.STATUS_PENDING_ACCEPTANCE -> {
                // 并发/重复提交：锁内重检时已经有待验收结论在等，后提交不生效
                DefectAcceptance open = findOpenAcceptance(id);
                throw new BizException("第 " + (open == null ? "?" : open.getRoundNo())
                        + " 轮处理结果已提交，正在等待 " + (open == null ? "" : open.getProcessedBy())
                        + " 之外的值班员验收，请勿重复提交");
            }
            case Defect.STATUS_ACCEPTED -> throw new BizException("缺陷已验收闭环，处理结果不能再修改");
            case Defect.STATUS_PROCESSING -> {
                // 正常提交（首轮或驳回返工后的再次提交）
            }
            default -> throw new BizException("当前状态不允许提交处理结果");
        }

        LocalDateTime now = LocalDateTime.now();
        // 轮次 = 历史已产生的处理/验收记录数 + 1（驳回返工后重新提交即新一轮）
        int round = (int) acceptanceRepository.countByDefectId(id) + 1;

        DefectAcceptance acceptance = new DefectAcceptance();
        acceptance.setDefectId(id);
        acceptance.setRoundNo(round);
        acceptance.setProcessedBy(processor);
        acceptance.setProcessResult(request.getProcessResult().trim());
        acceptance.setSubmittedTime(now);
        acceptanceRepository.save(acceptance);

        // 本次处理结果与人留痕在主表快照上；驳回返工时这些内容同样保留
        defect.setStatus(Defect.STATUS_PENDING_ACCEPTANCE);
        defect.setProcessResult(request.getProcessResult().trim());
        defect.setProcessedBy(processor);
        defect.setProcessedTime(now);
        if (defect.getReworkCount() == null) {
            defect.setReworkCount(0);
        }
        Defect saved = defectRepository.save(defect);
        return enrich(List.of(saved)).get(0);
    }

    /**
     * 第四步（验收人侧，独立第三方）：待验收 → 验收通过闭环 / 驳回退回处理中返工。
     * 验收人必须不同于本轮处理人；两名验收人并发提交时只生效一次。
     */
    @Transactional
    public DefectVO accept(Long id, DefectAcceptRequest request) {
        Defect defect = lockDefect(id);
        if (request == null || request.getAcceptor() == null || request.getAcceptor().isBlank()) {
            throw new BizException("验收人不能为空");
        }
        String acceptor = request.getAcceptor().trim();

        if (Defect.STATUS_ACCEPTED.equals(defect.getStatus())) {
            throw new BizException("该缺陷已由 " + nullToDash(defect.getAcceptedBy()) + " 验收闭环，本次验收未重复生效");
        }
        if (!Defect.STATUS_PENDING_ACCEPTANCE.equals(defect.getStatus())) {
            throw new BizException("缺陷当前为「" + Labels.defectStatus(defect.getStatus())
                    + "」，尚没有待验收的处理结果，不能验收");
        }

        // 锁内取本轮待验收记录；并发验收的后提交者会看到结论已被前一人落定
        DefectAcceptance open = findOpenAcceptance(id);
        if (open == null) {
            throw new BizException("未找到待验收的处理结果记录，请刷新后查看最新阶段");
        }
        if (acceptor.equals(open.getProcessedBy())) {
            throw new BizException("验收人【" + acceptor + "】就是本轮处理人，处理人不能验收自己的消缺结果，"
                    + "请由另一名值班员独立验收");
        }

        LocalDateTime now = LocalDateTime.now();
        open.setAcceptedBy(acceptor);
        open.setAcceptedTime(now);

        if (request.isPass()) {
            open.setResult(DefectAcceptance.RESULT_PASS);
            open.setAcceptOpinion(blankToNull(request.getOpinion()));
            acceptanceRepository.save(open);

            defect.setStatus(Defect.STATUS_ACCEPTED);
            defect.setAcceptedBy(acceptor);
            defect.setClosedTime(now);
            defectRepository.save(defect);
        } else {
            String reason = request.getOpinion();
            if (reason == null || reason.isBlank()) {
                throw new BizException("驳回返工时必须填写驳回原因，处理人将据此返工，本次验收未生效");
            }
            open.setResult(DefectAcceptance.RESULT_REJECTED);
            open.setAcceptOpinion(reason.trim());
            acceptanceRepository.save(open);

            // 退回处理中：处理结果、处理人、验收人、驳回原因全部保留，返工次数 +1
            defect.setStatus(Defect.STATUS_PROCESSING);
            defect.setReworkCount((defect.getReworkCount() == null ? 0 : defect.getReworkCount()) + 1);
            // 清空主表上的「最近一次提交」快照不合适——要求保留本次处理，故不清空；
            // status 回到 PROCESSING 已表示需要处理人重新提交新一轮结果。
            defectRepository.save(defect);
        }

        return enrich(List.of(defect)).get(0);
    }

    /** 取缺陷行级悲观锁，保证派工/开工/提交/验收串行，缺陷不存在直接报错 */
    private Defect lockDefect(Long id) {
        return defectRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("缺陷单不存在"));
    }

    private DefectAcceptance findOpenAcceptance(Long defectId) {
        List<DefectAcceptance> opens = acceptanceRepository.findByDefectIdAndResultIsNullOrderByIdAsc(defectId);
        return opens.isEmpty() ? null : opens.get(opens.size() - 1);
    }

    private String defOwner(Defect d) {
        return d.getOwner() == null || d.getOwner().isBlank() ? "未派工" : d.getOwner();
    }

    private String nullToDash(String v) {
        return v == null ? "—" : v;
    }

    private String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    // ============================================================
    // 视图组装
    // ============================================================

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
            applyStage(vo, d);

            List<DefectAcceptanceVO> accVos = new ArrayList<>();
            for (DefectAcceptance a : acceptanceRepository.findByDefectIdOrderByRoundNoAscIdAsc(d.getId())) {
                DefectAcceptanceVO avo = DefectAcceptanceVO.from(a);
                avo.setResultName(a.getResult() == null ? "待验收" : Labels.defectAcceptResult(a.getResult()));
                accVos.add(avo);
            }
            vo.setAcceptances(accVos);

            vos.add(vo);
        }
        return vos;
    }

    /**
     * 按真实阶段填充下一环节与页面可做操作——刷新/重新进入后看到的就是库内真实状态，
     * 页面、接口、统计与工作票终结判断共用同一套口径。
     */
    private void applyStage(DefectVO vo, Defect d) {
        String status = d.getStatus();
        vo.setClosed(Defect.STATUS_ACCEPTED.equals(status));
        vo.setPendingAcceptance(Defect.STATUS_PENDING_ACCEPTANCE.equals(status));
        vo.setCanDispatch(Defect.STATUS_REGISTERED.equals(status));
        vo.setCanStartProcess(Defect.STATUS_DISPATCHED.equals(status));
        vo.setCanSubmitResult(Defect.STATUS_PROCESSING.equals(status));
        vo.setCanAccept(Defect.STATUS_PENDING_ACCEPTANCE.equals(status));

        switch (status) {
            case Defect.STATUS_REGISTERED -> {
                vo.setNextStage(Defect.STATUS_DISPATCHED);
                vo.setWaitingText("待派工：指定消缺负责人");
            }
            case Defect.STATUS_DISPATCHED -> {
                vo.setNextStage(Defect.STATUS_PROCESSING);
                vo.setWaitingText("已派工给 " + defOwner(d) + "，待开工处理");
            }
            case Defect.STATUS_PROCESSING -> {
                boolean rework = d.getReworkCount() != null && d.getReworkCount() > 0;
                vo.setNextStage(Defect.STATUS_PENDING_ACCEPTANCE);
                vo.setWaitingText(rework
                        ? "验收被驳回已退回处理中（第 " + d.getReworkCount() + " 次返工），由 "
                                + defOwner(d) + " 重新处理并再次提交结果"
                        : "处理中：由 " + defOwner(d) + " 处理并提交结果，提交后进入待验收");
            }
            case Defect.STATUS_PENDING_ACCEPTANCE -> {
                vo.setNextStage(Defect.STATUS_ACCEPTED);
                vo.setWaitingText("待验收：须由 " + nullToDash(d.getProcessedBy())
                        + " 之外的值班员填写结论，通过才闭环，驳回则退回处理中返工");
            }
            case Defect.STATUS_ACCEPTED -> {
                vo.setNextStage(null);
                vo.setWaitingText("已验收闭环（验收人：" + nullToDash(d.getAcceptedBy()) + "）");
            }
            default -> {
                vo.setNextStage(null);
                vo.setWaitingText(Labels.defectStatus(status));
            }
        }
        vo.setNextStageName(vo.getNextStage() == null ? "" : Labels.defectStatus(vo.getNextStage()));
    }
}
