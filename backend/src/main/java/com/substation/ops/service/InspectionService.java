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
import com.substation.ops.domain.DefectClosureRound;
import com.substation.ops.domain.DefectSummary;
import com.substation.ops.domain.Equipment;
import com.substation.ops.domain.InspectionPlan;
import com.substation.ops.domain.InspectionRecord;
import com.substation.ops.dto.DefectAcceptRequest;
import com.substation.ops.dto.DefectCreateRequest;
import com.substation.ops.dto.DefectProcessRequest;
import com.substation.ops.dto.DefectRoundVO;
import com.substation.ops.dto.DefectVO;
import com.substation.ops.dto.OverdueDefectRow;
import com.substation.ops.repository.BayRepository;
import com.substation.ops.repository.DefectClosureRoundRepository;
import com.substation.ops.repository.DefectRepository;
import com.substation.ops.repository.DefectSummaryRepository;
import com.substation.ops.repository.EquipmentRepository;
import com.substation.ops.repository.InspectionPlanRepository;
import com.substation.ops.repository.InspectionRecordRepository;

/**
 * 模块三：巡检与缺陷。
 *
 * 缺陷闭环：登记 → 派工 → 处理（处理人报完工）→ 待验收（另一名值班员独立验收）→ 验收通过闭环；
 * 验收驳回则退回处理中返工，本轮处理/验收人/驳回原因全部保留（{@link DefectClosureRound}）。
 *
 * 报完工、验收都先取 defect 行级悲观锁（findByIdForUpdate）并在锁内做状态重检：
 * 重复提交、两名值班员同时验收只会有一次生效，后提交者看到的是最新结果。
 */
@Service
public class InspectionService {

    private final InspectionPlanRepository planRepository;
    private final InspectionRecordRepository recordRepository;
    private final DefectRepository defectRepository;
    private final DefectClosureRoundRepository roundRepository;
    private final DefectSummaryRepository defectSummaryRepository;
    private final BayRepository bayRepository;
    private final EquipmentRepository equipmentRepository;

    public InspectionService(InspectionPlanRepository planRepository,
                             InspectionRecordRepository recordRepository,
                             DefectRepository defectRepository,
                             DefectClosureRoundRepository roundRepository,
                             DefectSummaryRepository defectSummaryRepository,
                             BayRepository bayRepository,
                             EquipmentRepository equipmentRepository) {
        this.planRepository = planRepository;
        this.recordRepository = recordRepository;
        this.defectRepository = defectRepository;
        this.roundRepository = roundRepository;
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
     * 只有验收通过（ACCEPTED）才出榜；待验收、驳回返工照样占着超期排行。
     */
    @Transactional(readOnly = true)
    public List<OverdueDefectRow> overdueRanking() {
        LocalDate today = LocalDate.now();
        List<OverdueDefectRow> rows = defectRepository.rankOverdue(Defect.STATUS_ACCEPTED, today);
        for (OverdueDefectRow row : rows) {
            row.setOverdueDays((int) ChronoUnit.DAYS.between(row.getDueDate(), today));
            row.setStatusName(Labels.defectStatus(row.getStatus()));
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
        defect.setRejectCount(0);
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
     * 派工推进：登记 → 派工 → 处理。
     * 注意：处理中以后的环节不能再走这个接口——处理人只能报完工（submitResult），
     * 验收必须由另一名值班员独立完成（accept），不允许在处理中一点「推进」就变成已验收。
     */
    @Transactional
    public DefectVO advance(Long id, String owner) {
        Defect defect = lockDefect(id);

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
            case Defect.STATUS_PROCESSING -> throw new BizException(
                    "缺陷正在处理中：处理人完成后请「提交处理结果」进入待验收，由另一名值班员验收，不能直接推进成已验收");
            case Defect.STATUS_PENDING_ACCEPTANCE -> throw new BizException(
                    "缺陷已提交处理结果，处于待验收：请由与处理人不同的值班员填写验收结论，此处不能再推进");
            case Defect.STATUS_ACCEPTED -> throw new BizException("缺陷单已验收闭环，无需再推进");
            default -> throw new BizException("当前环节不允许该操作");
        }

        Defect saved = defectRepository.save(defect);
        return enrich(List.of(saved)).get(0);
    }

    /**
     * 处理人报完工：提交本次处理结果，处理中（含驳回返工）→ 待验收。
     * 只落「提交」，不闭环；重复提交（已在待验收/已验收）在锁内状态重检时被拦下。
     */
    @Transactional
    public DefectVO submitResult(Long id, DefectProcessRequest request) {
        Defect defect = lockDefect(id);

        if (Defect.STATUS_ACCEPTED.equals(defect.getStatus())) {
            throw new BizException("缺陷已验收闭环，处理结果不能再更改");
        }
        if (Defect.STATUS_PENDING_ACCEPTANCE.equals(defect.getStatus())) {
            throw new BizException("处理结果已提交，当前为待验收，请勿重复报完工；如验收被驳回，可在退回处理中后重新提交");
        }
        if (!Defect.STATUS_PROCESSING.equals(defect.getStatus())) {
            throw new BizException("只有处理中的缺陷才能提交处理结果（当前为「"
                    + Labels.defectStatus(defect.getStatus()) + "」）");
        }

        String person = request.getPerson().trim();
        String result = request.getResult().trim();
        LocalDateTime now = LocalDateTime.now();

        int roundNo = (int) roundRepository.countByDefectId(id) + 1;
        DefectClosureRound round = new DefectClosureRound();
        round.setDefectId(id);
        round.setRoundNo(roundNo);
        round.setProcessPerson(person);
        round.setProcessResult(result);
        round.setProcessTime(now);
        round.setStatus(DefectClosureRound.STATUS_SUBMITTED);
        roundRepository.save(round);

        defect.setStatus(Defect.STATUS_PENDING_ACCEPTANCE);
        defect.setProcessPerson(person);
        defect.setProcessResult(result);
        defect.setProcessTime(now);
        Defect saved = defectRepository.save(defect);
        return enrich(List.of(saved)).get(0);
    }

    /**
     * 独立验收：待验收 →（通过）已验收闭环 /（驳回）退回处理中返工。
     * 验收人必须与本次处理人不同；驳回必须填原因。
     * 行级悲观锁 + 状态重检：两人同时验收只有一次生效，后提交者看到已出结论。
     */
    @Transactional
    public DefectVO accept(Long id, DefectAcceptRequest request) {
        Defect defect = lockDefect(id);

        if (Defect.STATUS_ACCEPTED.equals(defect.getStatus())) {
            // 并发验收：后提交者直接看到已由谁验收通过的最新结果，不重复生效
            throw new BizException("该缺陷已由 " + defect.getAcceptPerson() + " 于 " + defect.getAcceptTime()
                    + " 验收通过，本次提交未重复生效");
        }
        if (!Defect.STATUS_PENDING_ACCEPTANCE.equals(defect.getStatus())) {
            throw new BizException("只有待验收的缺陷才能填写验收结论（当前为「"
                    + Labels.defectStatus(defect.getStatus()) + "」），请先由处理人提交处理结果");
        }

        String person = request.getPerson().trim();
        String verdict = request.getVerdict() == null ? "" : request.getVerdict().trim();
        String comment = request.getComment() == null ? "" : request.getComment().trim();

        // 独立验收：验收值班员不得与本次处理人是同一人
        if (person.equals(defect.getProcessPerson())) {
            throw new BizException("验收人不得与本次处理人（" + defect.getProcessPerson()
                    + "）为同一人，消缺必须由另一名值班员独立验收");
        }

        LocalDateTime now = LocalDateTime.now();
        DefectClosureRound round = roundRepository.findByDefectIdOrderByRoundNoAscIdAsc(id).stream()
                .reduce((first, second) -> second)
                .orElseThrow(() -> new BizException("未找到本轮处理记录，无法验收"));
        if (!DefectClosureRound.STATUS_SUBMITTED.equals(round.getStatus())) {
            // 锁内再次确认本轮尚未被验收（双保险，正常被前置状态重检拦住）
            throw new BizException("本轮处理已出具验收结论，本次提交未重复生效");
        }

        switch (verdict) {
            case Defect.ACCEPT_PASS -> {
                round.setAcceptVerdict(Defect.ACCEPT_PASS);
                round.setAcceptComment(comment);
                round.setAcceptPerson(person);
                round.setAcceptTime(now);
                round.setStatus(DefectClosureRound.STATUS_PASS);
                roundRepository.save(round);

                defect.setStatus(Defect.STATUS_ACCEPTED);
                defect.setAcceptPerson(person);
                defect.setAcceptResult(comment.isBlank() ? "验收合格，同意闭环" : comment);
                defect.setAcceptTime(now);
                defect.setClosedTime(now);
            }
            case Defect.ACCEPT_REJECT -> {
                if (comment.isBlank()) {
                    throw new BizException("驳回返工必须填写原因，说明未通过验收的问题，便于处理人据此返工");
                }
                round.setAcceptVerdict(Defect.ACCEPT_REJECT);
                round.setAcceptComment(comment);
                round.setAcceptPerson(person);
                round.setAcceptTime(now);
                round.setStatus(DefectClosureRound.STATUS_REJECTED);
                roundRepository.save(round);

                // 退回处理中：本次处理、验收人、驳回原因与历次轮次全部保留
                defect.setStatus(Defect.STATUS_PROCESSING);
                defect.setAcceptPerson(person);
                defect.setAcceptResult(comment);
                defect.setAcceptTime(now);
                defect.setRejectCount((defect.getRejectCount() == null ? 0 : defect.getRejectCount()) + 1);
            }
            default -> throw new BizException("验收结论只能是「通过」或「驳回」");
        }

        Defect saved = defectRepository.save(defect);
        return enrich(List.of(saved)).get(0);
    }

    /** 取缺陷行级悲观锁，报完工/验收串行化，重复提交、并发验收只生效一次 */
    private Defect lockDefect(Long id) {
        return defectRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("缺陷单不存在"));
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

        List<Long> ids = defects.stream().map(Defect::getId).toList();
        Map<Long, List<DefectClosureRound>> roundMap = new HashMap<>();
        if (!ids.isEmpty()) {
            for (DefectClosureRound r : roundRepository.findByDefectIdInOrderByDefectIdAscRoundNoAscIdAsc(ids)) {
                roundMap.computeIfAbsent(r.getDefectId(), k -> new ArrayList<>()).add(r);
            }
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

            List<DefectRoundVO> roundVOs = new ArrayList<>();
            for (DefectClosureRound r : roundMap.getOrDefault(d.getId(), List.of())) {
                String verdictName = r.getAcceptVerdict() == null
                        ? "" : Labels.defectAcceptVerdict(r.getAcceptVerdict());
                roundVOs.add(DefectRoundVO.from(r, verdictName, Labels.defectRoundStatus(r.getStatus())));
            }
            vo.setRounds(roundVOs);

            fillStage(vo, d);
            vos.add(vo);
        }
        return vos;
    }

    /**
     * 按真实阶段给出下一环节与可操作标志。页面、接口、统计共用这一份口径，
     * 刷新后前端直接显示真实阶段和下一步，不靠前端自己推状态。
     */
    private void fillStage(DefectVO vo, Defect d) {
        boolean closed = Defect.STATUS_ACCEPTED.equals(d.getStatus());
        vo.setClosed(closed);
        vo.setCanDispatch(Defect.STATUS_REGISTERED.equals(d.getStatus()));
        vo.setCanStartProcess(Defect.STATUS_DISPATCHED.equals(d.getStatus()));
        vo.setCanSubmitResult(Defect.STATUS_PROCESSING.equals(d.getStatus()));
        vo.setCanAccept(Defect.STATUS_PENDING_ACCEPTANCE.equals(d.getStatus()));

        switch (d.getStatus()) {
            case Defect.STATUS_REGISTERED -> {
                vo.setNextStage(Defect.STATUS_DISPATCHED);
                vo.setNextActionText("待派工：指定消缺负责人");
            }
            case Defect.STATUS_DISPATCHED -> {
                vo.setNextStage(Defect.STATUS_PROCESSING);
                vo.setNextActionText("已派工，消缺负责人开始处理");
            }
            case Defect.STATUS_PROCESSING -> {
                vo.setNextStage(Defect.STATUS_PENDING_ACCEPTANCE);
                vo.setNextActionText(d.getRejectCount() != null && d.getRejectCount() > 0
                        ? "验收被驳回，处理人返工后重新提交处理结果（第 " + (d.getRejectCount() + 1) + " 次报完工）"
                        : "处理中：处理人完成后提交处理结果，进入待验收");
            }
            case Defect.STATUS_PENDING_ACCEPTANCE -> {
                vo.setNextStage(Defect.STATUS_ACCEPTED);
                vo.setNextActionText("待验收：由与处理人（" + d.getProcessPerson() + "）不同的值班员填写结论，通过才闭环");
            }
            default -> {
                vo.setNextStage(null);
                vo.setNextActionText("已验收闭环" + (d.getAcceptPerson() == null ? "" : "（验收人：" + d.getAcceptPerson() + "）"));
            }
        }
        vo.setNextStageName(vo.getNextStage() == null ? "" : Labels.defectStatus(vo.getNextStage()));
    }
}
