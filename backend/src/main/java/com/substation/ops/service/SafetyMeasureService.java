package com.substation.ops.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.substation.ops.common.BizException;
import com.substation.ops.common.Labels;
import com.substation.ops.domain.Bay;
import com.substation.ops.domain.OperationStep;
import com.substation.ops.domain.OperationTicket;
import com.substation.ops.domain.SafetyMeasure;
import com.substation.ops.domain.SafetyMeasureLog;
import com.substation.ops.domain.WorkTicket;
import com.substation.ops.dto.MeasureItemRequest;
import com.substation.ops.repository.BayRepository;
import com.substation.ops.repository.OperationStepRepository;
import com.substation.ops.repository.OperationTicketRepository;
import com.substation.ops.repository.SafetyMeasureLogRepository;
import com.substation.ops.repository.SafetyMeasureRepository;

/**
 * 安全措施逐项执行闭环的核心领域服务。
 *
 * 阶段：PENDING 待执行 → INSTALLED 已执行（装设/合上）→ TO_REMOVE 待拆除 → REMOVED 已拆除
 *
 * 两道闸门（工作票侧 + 关联操作票侧两侧都满足才放行）：
 *   - 许可：全部措施已装设，且关联操作票对应步骤已执行
 *   - 终结：需拆除措施全部拆除，且关联恢复操作票对应步骤已执行
 */
@Service
public class SafetyMeasureService {

    private final SafetyMeasureRepository measureRepository;
    private final SafetyMeasureLogRepository logRepository;
    private final OperationTicketRepository operationTicketRepository;
    private final OperationStepRepository stepRepository;
    private final BayRepository bayRepository;

    public SafetyMeasureService(SafetyMeasureRepository measureRepository,
                                SafetyMeasureLogRepository logRepository,
                                OperationTicketRepository operationTicketRepository,
                                OperationStepRepository stepRepository,
                                BayRepository bayRepository) {
        this.measureRepository = measureRepository;
        this.logRepository = logRepository;
        this.operationTicketRepository = operationTicketRepository;
        this.stepRepository = stepRepository;
        this.bayRepository = bayRepository;
    }

    // ============================================================
    // 编制
    // ============================================================

    /** 开票时把逐项措施写入（全部 PENDING），逐条留编制痕迹 */
    @Transactional
    public void createMeasures(Long ticketId, List<MeasureItemRequest> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        int seq = 0;
        for (MeasureItemRequest item : items) {
            seq++;
            SafetyMeasure m = new SafetyMeasure();
            m.setTicketId(ticketId);
            m.setSeqNo(seq);
            m.setVersionNo(1);
            m.setMeasureType(item.getMeasureType());
            m.setLocationBayId(item.getLocationBayId());
            m.setLocationDesc(item.getLocationDesc());
            m.setRequiredAction(item.getRequiredAction());
            m.setNeedRestore(item.getNeedRestore() == null ? Boolean.TRUE : item.getNeedRestore());
            m.setInstallTicketId(item.getInstallTicketId());
            m.setInstallStepNo(item.getInstallStepNo());
            m.setRestoreTicketId(item.getRestoreTicketId());
            m.setRestoreStepNo(item.getRestoreStepNo());
            m.setStatus(SafetyMeasure.STATUS_PENDING);
            m.setVersion(0);
            SafetyMeasure saved = measureRepository.saveAndFlush(m);
            writeLog(saved, SafetyMeasureLog.ACTION_CREATE, null, SafetyMeasure.STATUS_PENDING,
                    "系统", "安全措施随工作票编制入票");
        }
    }

    // ============================================================
    // 确认：装设 / 拆除
    // ============================================================

    /**
     * 装设/合上确认：只能对 PENDING 的现行措施操作。
     * 并发由调用方（WorkTicketService）持票行悲观锁串行化，这里再做状态重检。
     */
    @Transactional
    public SafetyMeasure confirmInstall(Long ticketId, Long measureId, String person) {
        SafetyMeasure m = requireActiveMeasure(ticketId, measureId);
        if (!SafetyMeasure.STATUS_PENDING.equals(m.getStatus())) {
            throw new BizException("该措施当前为「" + Labels.measureStatus(m.getStatus())
                    + "」，不能重复装设确认（执行人：" + person + " 的提交未生效，现场仍是最新状态）");
        }
        String from = m.getStatus();
        m.setInstallPerson(person);
        m.setInstallTime(LocalDateTime.now());
        m.setStatus(SafetyMeasure.STATUS_INSTALLED);
        m.setVersion(m.getVersion() == null ? 0 : m.getVersion() + 1);
        SafetyMeasure saved = measureRepository.saveAndFlush(m);
        writeLog(saved, SafetyMeasureLog.ACTION_INSTALL, from, SafetyMeasure.STATUS_INSTALLED,
                person, m.getRequiredAction() + "（现场逐项确认）");
        return saved;
    }

    /**
     * 拆除/取下确认：只有「待拆除」阶段（已通知拆除）的措施可拆除。
     */
    @Transactional
    public SafetyMeasure confirmRemove(Long ticketId, Long measureId, String person) {
        SafetyMeasure m = requireActiveMeasure(ticketId, measureId);
        if (SafetyMeasure.STATUS_REMOVED.equals(m.getStatus())) {
            throw new BizException("该措施已拆除，不能重复登记（" + person + " 的提交未生效）");
        }
        if (!SafetyMeasure.STATUS_TO_REMOVE.equals(m.getStatus())) {
            throw new BizException("尚未通知拆除安措，当前阶段为「" + Labels.measureStatus(m.getStatus())
                    + "」，不能提前拆除");
        }
        String from = m.getStatus();
        m.setRemovePerson(person);
        m.setRemoveTime(LocalDateTime.now());
        m.setStatus(SafetyMeasure.STATUS_REMOVED);
        m.setVersion(m.getVersion() == null ? 0 : m.getVersion() + 1);
        SafetyMeasure saved = measureRepository.saveAndFlush(m);
        writeLog(saved, SafetyMeasureLog.ACTION_REMOVE, from, SafetyMeasure.STATUS_REMOVED,
                person, m.getRequiredAction() + " 已拆除恢复");
        return saved;
    }

    // ============================================================
    // 撤回许可：现场恢复到「重新待执行」，但痕迹不抹掉
    // ============================================================

    /**
     * 撤回许可后对每条现行措施回置：
     *   REMOVED 已拆除 → PENDING 待执行（原拆除人/时间保留在痕迹中）
     *   TO_REMOVE 待拆除 → PENDING 待执行
     *   INSTALLED 已装设 → 保持装设不动（现场安全措施不能因撤票擅拆）
     *   不需拆除(needRestore=false)且已装设的保持 INSTALLED
     */
    @Transactional
    public void reactivateAfterWithdraw(Long ticketId, String person, String reason) {
        List<SafetyMeasure> all = activeMeasures(ticketId);
        for (SafetyMeasure m : all) {
            String status = m.getStatus();
            boolean rollback = SafetyMeasure.STATUS_TO_REMOVE.equals(status)
                    || SafetyMeasure.STATUS_REMOVED.equals(status);
            if (!rollback) {
                continue;
            }
            String from = status;
            m.setStatus(SafetyMeasure.STATUS_PENDING);
            // 原拆除结果与原装设确认均属于被撤回许可之前的现场，按规程须重新逐项确认；
            // 历史装拆人/时间已写入 safety_measure_log，不会被抹掉
            m.setRemovePerson(null);
            m.setRemoveTime(null);
            m.setInstallPerson(null);
            m.setInstallTime(null);
            m.setVersion(m.getVersion() == null ? 0 : m.getVersion() + 1);
            SafetyMeasure saved = measureRepository.saveAndFlush(m);
            writeLog(saved, SafetyMeasureLog.ACTION_REACTIVATE, from, SafetyMeasure.STATUS_PENDING,
                    person, "撤回许可，措施重新回到待执行。原因：" + reason
                            + "（原装设/拆除记录已保留在痕迹中）");
        }
    }

    // ============================================================
    // 现场变更：原行 VOIDED 留痕，新版本行回到 PENDING
    // ============================================================

    @Transactional
    public SafetyMeasure changeMeasure(Long ticketId, Long measureId, MeasureItemRequest item,
                                       String person, String reason) {
        SafetyMeasure old = requireActiveMeasure(ticketId, measureId);
        if (SafetyMeasure.STATUS_REMOVED.equals(old.getStatus())) {
            throw new BizException("该措施已拆除，不存在变更需求；如需重新装设请走新增措施");
        }

        // 原版本作废留痕（已装设人/时间等列原样保留，不删除）
        String oldStatus = old.getStatus();
        old.setStatus(SafetyMeasure.STATUS_VOIDED);
        old.setVoidReason(reason);
        old.setVersion(old.getVersion() == null ? 0 : old.getVersion() + 1);
        SafetyMeasure voided = measureRepository.saveAndFlush(old);
        writeLog(voided, SafetyMeasureLog.ACTION_VOID, oldStatus, SafetyMeasure.STATUS_VOIDED,
                person, "现场变更，原措施作废（" + reason + "），原装设确认痕迹保留");

        // 同序号新版本，从 PENDING 重新走闭环
        SafetyMeasure m = new SafetyMeasure();
        m.setTicketId(ticketId);
        m.setSeqNo(old.getSeqNo());
        m.setVersionNo(old.getVersionNo() + 1);
        m.setParentMeasureId(old.getId());
        m.setMeasureType(item == null || item.getMeasureType() == null ? old.getMeasureType() : item.getMeasureType());
        m.setLocationBayId(item == null || item.getLocationBayId() == null ? old.getLocationBayId() : item.getLocationBayId());
        m.setLocationDesc(item == null || item.getLocationDesc() == null ? old.getLocationDesc() : item.getLocationDesc());
        m.setRequiredAction(item == null || item.getRequiredAction() == null ? old.getRequiredAction() : item.getRequiredAction());
        m.setNeedRestore(item == null || item.getNeedRestore() == null ? old.getNeedRestore() : item.getNeedRestore());
        m.setInstallTicketId(item == null ? old.getInstallTicketId() : item.getInstallTicketId());
        m.setInstallStepNo(item == null ? old.getInstallStepNo() : item.getInstallStepNo());
        m.setRestoreTicketId(item == null ? old.getRestoreTicketId() : item.getRestoreTicketId());
        m.setRestoreStepNo(item == null ? old.getRestoreStepNo() : item.getRestoreStepNo());
        m.setStatus(SafetyMeasure.STATUS_PENDING);
        m.setVersion(0);
        SafetyMeasure saved = measureRepository.saveAndFlush(m);
        writeLog(saved, SafetyMeasureLog.ACTION_CREATE, null, SafetyMeasure.STATUS_PENDING,
                person, "现场变更新增措施（原序号第 " + old.getSeqNo() + " 项新版本），待重新装设确认");
        return saved;
    }

    /** 撤回许可后新增一条措施（现场补充安措） */
    @Transactional
    public SafetyMeasure appendMeasure(Long ticketId, MeasureItemRequest item, String person, String reason) {
        int maxSeq = activeMeasures(ticketId).stream()
                .map(SafetyMeasure::getSeqNo)
                .max(Integer::compareTo)
                .orElse(0);
        SafetyMeasure m = new SafetyMeasure();
        m.setTicketId(ticketId);
        m.setSeqNo(maxSeq + 1);
        m.setVersionNo(1);
        m.setMeasureType(item.getMeasureType());
        m.setLocationBayId(item.getLocationBayId());
        m.setLocationDesc(item.getLocationDesc());
        m.setRequiredAction(item.getRequiredAction());
        m.setNeedRestore(item.getNeedRestore() == null ? Boolean.TRUE : item.getNeedRestore());
        m.setInstallTicketId(item.getInstallTicketId());
        m.setInstallStepNo(item.getInstallStepNo());
        m.setRestoreTicketId(item.getRestoreTicketId());
        m.setRestoreStepNo(item.getRestoreStepNo());
        m.setStatus(SafetyMeasure.STATUS_PENDING);
        m.setVersion(0);
        SafetyMeasure saved = measureRepository.saveAndFlush(m);
        writeLog(saved, SafetyMeasureLog.ACTION_CREATE, null, SafetyMeasure.STATUS_PENDING,
                person, "撤回许可后现场新增措施：" + reason);
        return saved;
    }

    // ============================================================
    // 闸门核验：许可 / 终结
    // ============================================================

    /**
     * 许可闸门：所有现行措施必须 INSTALLED，且每条绑定的装设操作步骤必须 DONE。
     * 返回结构化卡点清单；empty 表示两侧均满足。
     */
    @Transactional(readOnly = true)
    public GateResult evaluatePermitGate(Long ticketId) {
        return evaluate(ticketId, true);
    }

    /**
     * 终结闸门：needRestore=true 的现行措施必须 REMOVED，且绑定的恢复操作步骤必须 DONE。
     */
    @Transactional(readOnly = true)
    public GateResult evaluateTerminateGate(Long ticketId) {
        return evaluate(ticketId, false);
    }

    private GateResult evaluate(Long ticketId, boolean permitGate) {
        List<SafetyMeasure> measures = activeMeasures(ticketId);
        Map<Long, OperationTicket> opTicketMap = loadOperationTickets(measures);
        Map<String, OperationStep> stepIndex = loadRelatedSteps(measures);

        GateResult result = new GateResult();
        result.total = measures.size();

        for (SafetyMeasure m : measures) {
            // 终结时只核验需要拆除恢复的措施（验电、保持距离、监护类不参与拆除闸门）
            if (!permitGate && !Boolean.TRUE.equals(m.getNeedRestore())) {
                continue;
            }
            String requiredStatus = permitGate ? SafetyMeasure.STATUS_INSTALLED
                    : SafetyMeasure.STATUS_REMOVED;

            String label = "第" + m.getSeqNo() + "项·" + Labels.measureType(m.getMeasureType())
                    + "「" + m.getRequiredAction() + "」@ " + m.getLocationDesc();

            // —— 工作票侧：措施自身阶段 ——
            if (!requiredStatus.equals(m.getStatus())) {
                result.measureBlockers.add(new Blocker(label,
                        "工作票侧未完成：当前「" + Labels.measureStatus(m.getStatus())
                                + "」，应为「" + Labels.measureStatus(requiredStatus) + "」"));
            }

            // —— 操作票侧：关联步骤 ——
            // 接地线/接地刀闸/遮栏/标示牌及一切需拆除的实物措施，必须与倒闸操作步骤逐项核对；
            // 「保持安全距离/绝缘工具/专人监护」等作业方法类措施（OTHER 且无需拆除）无对应倒闸步骤，不强制绑定。
            boolean physicalMeasure = SafetyMeasure.TYPE_GROUND_LINE.equals(m.getMeasureType())
                    || SafetyMeasure.TYPE_EARTH_SWITCH.equals(m.getMeasureType())
                    || SafetyMeasure.TYPE_BARRIER.equals(m.getMeasureType())
                    || SafetyMeasure.TYPE_SIGNBOARD.equals(m.getMeasureType())
                    || Boolean.TRUE.equals(m.getNeedRestore());
            Long refTicketId = permitGate ? m.getInstallTicketId() : m.getRestoreTicketId();
            Integer refStepNo = permitGate ? m.getInstallStepNo() : m.getRestoreStepNo();
            String sideName = permitGate ? "装设" : "拆除恢复";
            boolean requireOpStep = permitGate ? physicalMeasure : Boolean.TRUE.equals(m.getNeedRestore());

            if (requireOpStep && (refTicketId == null || refStepNo == null)) {
                // 实物措施未绑定关联步骤：无法核对两票一致性，按卡点处理
                result.measureBlockers.add(new Blocker(label,
                        "关联操作票侧未核对：该措施未绑定" + sideName + "对应的倒闸操作步骤，无法确认两票一致"));
            } else if (refTicketId != null && refStepNo != null) {
                OperationTicket opTicket = opTicketMap.get(refTicketId);
                String key = refTicketId + "#" + refStepNo;
                OperationStep step = stepIndex.get(key);
                if (opTicket == null) {
                    result.measureBlockers.add(new Blocker(label,
                            "关联操作票侧未完成：绑定的操作票（id=" + refTicketId + "）不存在"));
                } else if (OperationTicket.STATUS_CANCELED.equals(opTicket.getStatus())) {
                    result.measureBlockers.add(new Blocker(label,
                            "关联操作票「" + opTicket.getTicketNo() + "」已作废，" + sideName + "步骤不可作为依据"));
                } else if (step == null) {
                    result.measureBlockers.add(new Blocker(label,
                            "关联操作票「" + opTicket.getTicketNo() + "」第 " + refStepNo + " 步不存在"));
                } else if (!OperationStep.STATUS_DONE.equals(step.getStatus())) {
                    String stepState = OperationStep.STATUS_FAILED.equals(step.getStatus())
                            ? "操作失败（" + step.getFailReason() + "）"
                            : Labels.stepStatus(step.getStatus());
                    result.measureBlockers.add(new Blocker(label,
                            "关联操作票「" + opTicket.getTicketNo() + "」第 " + refStepNo
                                    + " 步「" + step.getActionDesc() + "」" + stepState + "，" + sideName + "操作尚未完成"));
                }
            }
        }

        result.pass = result.measureBlockers.isEmpty();
        return result;
    }

    // ============================================================
    // 视图组装
    // ============================================================

    @Transactional(readOnly = true)
    public List<Map<String, Object>> measureViews(Long ticketId) {
        List<SafetyMeasure> all = measureRepository.findByTicketIdOrderBySeqNoAscVersionNoAscIdAsc(ticketId);
        Map<Long, OperationTicket> opTicketMap = loadOperationTickets(all);
        Map<Long, Bay> bayMap = loadBays(all);
        Map<String, OperationStep> stepIndex = loadRelatedSteps(all);

        List<Map<String, Object>> views = new ArrayList<>();
        for (SafetyMeasure m : all) {
            Map<String, Object> v = new HashMap<>();
            v.put("id", m.getId());
            v.put("seqNo", m.getSeqNo());
            v.put("versionNo", m.getVersionNo());
            v.put("parentMeasureId", m.getParentMeasureId());
            v.put("measureType", m.getMeasureType());
            v.put("measureTypeName", Labels.measureType(m.getMeasureType()));
            v.put("locationBayId", m.getLocationBayId());
            Bay bay = bayMap.get(m.getLocationBayId());
            v.put("locationBayName", bay == null ? "间隔#" + m.getLocationBayId() : bay.getName());
            v.put("locationDesc", m.getLocationDesc());
            v.put("requiredAction", m.getRequiredAction());
            v.put("needRestore", m.getNeedRestore());
            v.put("installPerson", m.getInstallPerson());
            v.put("installTime", m.getInstallTime());
            v.put("removePerson", m.getRemovePerson());
            v.put("removeTime", m.getRemoveTime());
            v.put("status", m.getStatus());
            v.put("statusName", Labels.measureStatus(m.getStatus()));
            v.put("voidReason", m.getVoidReason());
            v.put("remark", m.getRemark());
            v.put("voided", SafetyMeasure.STATUS_VOIDED.equals(m.getStatus()));
            v.put("installStep", refStepView(m.getInstallTicketId(), m.getInstallStepNo(), opTicketMap, stepIndex));
            v.put("restoreStep", refStepView(m.getRestoreTicketId(), m.getRestoreStepNo(), opTicketMap, stepIndex));

            // 前端动作门禁用的派生标记
            v.put("canInstall", SafetyMeasure.STATUS_PENDING.equals(m.getStatus()));
            v.put("canRemove", SafetyMeasure.STATUS_TO_REMOVE.equals(m.getStatus()));
            v.put("canChange", !SafetyMeasure.STATUS_REMOVED.equals(m.getStatus())
                    && !SafetyMeasure.STATUS_VOIDED.equals(m.getStatus()));
            views.add(v);
        }
        return views;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> logViews(Long ticketId) {
        List<SafetyMeasureLog> logs = logRepository.findByTicketIdOrderByOperateTimeAscIdAsc(ticketId);
        List<Map<String, Object>> views = new ArrayList<>();
        for (SafetyMeasureLog log : logs) {
            Map<String, Object> v = new HashMap<>();
            v.put("id", log.getId());
            v.put("measureId", log.getMeasureId());
            v.put("action", log.getAction());
            v.put("actionName", Labels.measureAction(log.getAction()));
            v.put("fromStatus", log.getFromStatus());
            v.put("fromStatusName", log.getFromStatus() == null ? "" : Labels.measureStatus(log.getFromStatus()));
            v.put("toStatus", log.getToStatus());
            v.put("toStatusName", log.getToStatus() == null ? "" : Labels.measureStatus(log.getToStatus()));
            v.put("operatorName", log.getOperatorName());
            v.put("operateTime", log.getOperateTime());
            v.put("detail", log.getDetail());
            views.add(v);
        }
        return views;
    }

    // ============================================================
    // 工具
    // ============================================================

    private List<SafetyMeasure> activeMeasures(Long ticketId) {
        return measureRepository
                .findByTicketIdAndStatusNotOrderBySeqNoAscVersionNoAscIdAsc(ticketId, SafetyMeasure.STATUS_VOIDED);
    }

    private SafetyMeasure requireActiveMeasure(Long ticketId, Long measureId) {
        SafetyMeasure m = measureRepository.findById(measureId)
                .orElseThrow(() -> new BizException("安全措施不存在"));
        if (!Objects.equals(m.getTicketId(), ticketId)) {
            throw new BizException("该安全措施不属于当前工作票");
        }
        if (SafetyMeasure.STATUS_VOIDED.equals(m.getStatus())) {
            throw new BizException("该措施已现场变更作废，请对其新版本操作（原确认痕迹保留可查）");
        }
        return m;
    }

    private void writeLog(SafetyMeasure m, String action, String from, String to, String person, String detail) {
        SafetyMeasureLog log = new SafetyMeasureLog();
        log.setMeasureId(m.getId());
        log.setTicketId(m.getTicketId());
        log.setAction(action);
        log.setFromStatus(from);
        log.setToStatus(to);
        log.setOperatorName(person);
        log.setOperateTime(LocalDateTime.now());
        log.setDetail(detail);
        logRepository.save(log);
    }

    private Map<Long, OperationTicket> loadOperationTickets(List<SafetyMeasure> measures) {
        Set<Long> ids = new HashSet<>();
        for (SafetyMeasure m : measures) {
            if (m.getInstallTicketId() != null) {
                ids.add(m.getInstallTicketId());
            }
            if (m.getRestoreTicketId() != null) {
                ids.add(m.getRestoreTicketId());
            }
        }
        Map<Long, OperationTicket> map = new HashMap<>();
        if (!ids.isEmpty()) {
            for (OperationTicket t : operationTicketRepository.findAllById(ids)) {
                map.put(t.getId(), t);
            }
        }
        return map;
    }

    private Map<String, OperationStep> loadRelatedSteps(List<SafetyMeasure> measures) {
        Set<Long> ticketIds = new HashSet<>();
        for (SafetyMeasure m : measures) {
            if (m.getInstallTicketId() != null) {
                ticketIds.add(m.getInstallTicketId());
            }
            if (m.getRestoreTicketId() != null) {
                ticketIds.add(m.getRestoreTicketId());
            }
        }
        Map<String, OperationStep> index = new HashMap<>();
        if (!ticketIds.isEmpty()) {
            for (OperationStep s : stepRepository.findByTicketIdInOrderByTicketIdAscStepNoAsc(new ArrayList<>(ticketIds))) {
                index.put(s.getTicketId() + "#" + s.getStepNo(), s);
            }
        }
        return index;
    }

    private Map<Long, Bay> loadBays(List<SafetyMeasure> measures) {
        Set<Long> ids = measures.stream().map(SafetyMeasure::getLocationBayId).collect(Collectors.toSet());
        Map<Long, Bay> map = new HashMap<>();
        for (Bay b : bayRepository.findAllById(ids)) {
            map.put(b.getId(), b);
        }
        return map;
    }

    private Map<String, Object> refStepView(Long ticketId, Integer stepNo,
                                            Map<Long, OperationTicket> opTicketMap,
                                            Map<String, OperationStep> stepIndex) {
        Map<String, Object> v = new HashMap<>();
        if (ticketId == null || stepNo == null) {
            v.put("bound", false);
            return v;
        }
        v.put("bound", true);
        v.put("ticketId", ticketId);
        v.put("stepNo", stepNo);
        OperationTicket op = opTicketMap.get(ticketId);
        v.put("ticketNo", op == null ? "操作票#" + ticketId : op.getTicketNo());
        v.put("ticketStatus", op == null ? null : op.getStatus());
        v.put("ticketStatusName", op == null ? "" : Labels.opStatus(op.getStatus()));
        OperationStep step = stepIndex.get(ticketId + "#" + stepNo);
        if (step == null) {
            v.put("status", "MISSING");
            v.put("statusName", "步骤缺失");
            v.put("done", false);
        } else {
            v.put("actionDesc", step.getActionDesc());
            v.put("status", step.getStatus());
            v.put("statusName", Labels.stepStatus(step.getStatus()));
            v.put("done", OperationStep.STATUS_DONE.equals(step.getStatus()));
            v.put("executePerson", step.getExecutePerson());
            v.put("executeTime", step.getExecuteTime());
            v.put("failReason", step.getFailReason());
        }
        return v;
    }

    /** 闸门核验结果 */
    public static class GateResult {
        public boolean pass;
        public int total;
        public final List<Blocker> measureBlockers = new ArrayList<>();

        public List<String> messages() {
            return measureBlockers.stream().map(b -> b.source + " —— " + b.reason).toList();
        }
    }

    public static class Blocker {
        public final String source;
        public final String reason;

        public Blocker(String source, String reason) {
            this.source = source;
            this.reason = reason;
        }
    }

    /** 供工作票服务判断当前许可子阶段用 */
    @Transactional(readOnly = true)
    public boolean allRemovedForTerminate(Long ticketId) {
        return evaluateTerminateGate(ticketId).pass;
    }

    /** 工作票作废级联：仅日志保留，措施标记 VOIDED，避免作废后仍被核验 */
    @Transactional
    public void voidAllByCancel(Long ticketId, String person, String reason) {
        for (SafetyMeasure m : activeMeasures(ticketId)) {
            String from = m.getStatus();
            m.setStatus(SafetyMeasure.STATUS_VOIDED);
            m.setVoidReason("工作票作废：" + reason);
            m.setVersion(m.getVersion() == null ? 0 : m.getVersion() + 1);
            SafetyMeasure saved = measureRepository.saveAndFlush(m);
            writeLog(saved, SafetyMeasureLog.ACTION_VOID, from, SafetyMeasure.STATUS_VOIDED,
                    person, "工作票作废，措施整体失效，历史确认记录保留");
        }
    }

    /** 阶段统计（票列表徽标用）：key = 阶段，value = 数量 */
    @Transactional(readOnly = true)
    public Map<String, Long> statusCount(Long ticketId) {
        Map<String, Long> counts = new HashMap<>();
        for (SafetyMeasure m : activeMeasures(ticketId)) {
            counts.merge(m.getStatus(), 1L, Long::sum);
        }
        return counts;
    }

    /** 通知拆除：把已装设且需拆除的措施推进到 TO_REMOVE */
    @Transactional
    public void markToRemove(Long ticketId, String person) {
        for (SafetyMeasure m : activeMeasures(ticketId)) {
            if (SafetyMeasure.STATUS_INSTALLED.equals(m.getStatus())
                    && Boolean.TRUE.equals(m.getNeedRestore())) {
                m.setStatus(SafetyMeasure.STATUS_TO_REMOVE);
                m.setVersion(m.getVersion() == null ? 0 : m.getVersion() + 1);
                measureRepository.save(m);
            }
        }
    }

    /** 是否还有需要拆除、且未拆除的现行措施（未到 TO_REMOVE 的也算未完成拆除） */
    @Transactional(readOnly = true)
    public boolean hasRestorablePending(Long ticketId) {
        return activeMeasures(ticketId).stream()
                .filter(m -> Boolean.TRUE.equals(m.getNeedRestore()))
                .anyMatch(m -> !SafetyMeasure.STATUS_REMOVED.equals(m.getStatus()));
    }
}
