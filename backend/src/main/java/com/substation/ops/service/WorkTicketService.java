package com.substation.ops.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
import com.substation.ops.domain.SafetyMeasure;
import com.substation.ops.domain.WorkTicket;
import com.substation.ops.domain.WorkTicketEvent;
import com.substation.ops.dto.MeasureItemRequest;
import com.substation.ops.dto.TicketStatusCount;
import com.substation.ops.dto.WorkTicketCreateRequest;
import com.substation.ops.repository.BayRepository;
import com.substation.ops.repository.DefectRepository;
import com.substation.ops.repository.WorkTicketEventRepository;
import com.substation.ops.repository.WorkTicketRepository;

/**
 * 模块四：工作票。核心是三审（签发 / 许可 / 终结）与安全措施逐项执行闭环。
 *
 * 所有会推进票面的动作都先取 work_ticket 行级悲观锁（findByIdForUpdate），
 * 两名值班员并发确认同一条措施、并发许可时自动串行：后提交者在锁内重新读到
 * 最新阶段并被状态重检拦下，不会产生重复执行记录，也越不过未完成项目。
 */
@Service
public class WorkTicketService {

    private final WorkTicketRepository workTicketRepository;
    private final WorkTicketEventRepository eventRepository;
    private final BayRepository bayRepository;
    private final DefectRepository defectRepository;
    private final SafetyMeasureService measureService;

    public WorkTicketService(WorkTicketRepository workTicketRepository,
                             WorkTicketEventRepository eventRepository,
                             BayRepository bayRepository,
                             DefectRepository defectRepository,
                             SafetyMeasureService measureService) {
        this.workTicketRepository = workTicketRepository;
        this.eventRepository = eventRepository;
        this.bayRepository = bayRepository;
        this.defectRepository = defectRepository;
        this.measureService = measureService;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listOfDay(LocalDate date) {
        LocalDate day = date == null ? LocalDate.now() : date;
        List<WorkTicket> tickets = workTicketRepository.findByIssueDateOrderByIdAsc(day);
        Map<Long, Bay> bayMap = new HashMap<>();
        for (Bay b : bayRepository.findAll()) {
            bayMap.put(b.getId(), b);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (WorkTicket t : tickets) {
            result.add(toListMap(t, bayMap.get(t.getBayId())));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(Long id) {
        WorkTicket t = workTicketRepository.findById(id)
                .orElseThrow(() -> new BizException("工作票不存在"));
        Bay bay = t.getBayId() == null ? null : bayRepository.findById(t.getBayId()).orElse(null);
        return toDetailMap(t, bay);
    }

    /**
     * 新建工作票：票号交由存储过程生成后回写；安全措施逐项落表。
     */
    @Transactional
    public Map<String, Object> create(WorkTicketCreateRequest request) {
        Bay bay = bayRepository.findById(request.getBayId())
                .orElseThrow(() -> new BizException("所选间隔不存在"));

        // 规则一：运行中的间隔不允许下达检修工作票
        if (Bay.STATUS_RUNNING.equals(bay.getStatus())) {
            throw new BizException("间隔【" + bay.getName() + "】当前处于运行状态，不允许下达检修工作票，请先将间隔转冷备或检修");
        }

        LocalDate issueDate = request.getIssueDate() == null ? LocalDate.now() : request.getIssueDate();

        // 规则二：同一张间隔当天不能有两张并行的工作票
        boolean alreadyWorking = workTicketRepository.findByIssueDateOrderByIdAsc(issueDate).stream()
                .anyMatch(t -> t.getBayId().equals(bay.getId())
                        && (WorkTicket.STATUS_PERMITTED.equals(t.getStatus())
                        || WorkTicket.STATUS_ISSUED.equals(t.getStatus())
                        || WorkTicket.STATUS_DRAFT.equals(t.getStatus())));
        if (alreadyWorking) {
            throw new BizException("间隔【" + bay.getName() + "】在 " + issueDate + " 已有未终结的工作票，不能重复开票");
        }

        validateMeasureItems(request.getMeasures());

        String issueDateText = issueDate.toString();
        String ticketNo = workTicketRepository.nextTicketNo(bay.getId(), issueDateText);

        WorkTicket ticket = new WorkTicket();
        ticket.setBayId(bay.getId());
        ticket.setIssueDate(issueDate);
        ticket.setTicketNo(ticketNo);
        ticket.setWorkLeader(request.getWorkLeader());
        ticket.setTeamMembers(request.getTeamMembers());
        ticket.setWorkContent(request.getWorkContent());
        ticket.setPowerCutRange(request.getPowerCutRange());
        ticket.setSafetyMeasures(request.getSafetyMeasures());
        ticket.setPlanStartTime(request.getPlanStartTime());
        ticket.setPlanEndTime(request.getPlanEndTime());
        ticket.setStatus(WorkTicket.STATUS_DRAFT);

        WorkTicket saved = workTicketRepository.saveAndFlush(ticket);
        measureService.createMeasures(saved.getId(), request.getMeasures());

        return detail(saved.getId());
    }

    private void validateMeasureItems(List<MeasureItemRequest> items) {
        if (items == null || items.isEmpty()) {
            throw new BizException("请至少编制一项可逐项确认的安全措施（接地线/接地刀闸/遮栏/标示牌等），不能只填一段文字");
        }
    }

    // ============================================================
    // 安措确认（装设/拆除）
    // ============================================================

    @Transactional
    public Map<String, Object> installMeasure(Long id, Long measureId, String person) {
        WorkTicket ticket = lockTicket(id);
        if (WorkTicket.STATUS_TERMINATED.equals(ticket.getStatus())
                || WorkTicket.STATUS_CANCELED.equals(ticket.getStatus())) {
            throw new BizException("工作票当前为「" + Labels.workStatus(ticket.getStatus())
                    + "」，安措不能再变动");
        }
        // 已许可后不得直接确认新装设；现场变更必须先撤回许可
        if (WorkTicket.STATUS_PERMITTED.equals(ticket.getStatus())) {
            throw new BizException("工作票已许可，不得直接改动已确认安措；现场确有变更请先撤回许可，变更项会回到待执行并保留原痕迹");
        }
        measureService.confirmInstall(id, measureId, person);
        return detail(id);
    }

    @Transactional
    public Map<String, Object> removeMeasure(Long id, Long measureId, String person) {
        WorkTicket ticket = lockTicket(id);
        if (!WorkTicket.STATUS_PERMITTED.equals(ticket.getStatus())
                || !WorkTicket.PHASE_DEMOLISHING.equals(ticket.getPermitPhase())) {
            throw new BizException("只有许可开工且已通知拆除安措（拆除恢复阶段）后，才能逐项拆除");
        }
        measureService.confirmRemove(id, measureId, person);
        return detail(id);
    }

    /** 工作完成，许可人通知拆除安措：INSTALLED(needRestore) → TO_REMOVE */
    @Transactional
    public Map<String, Object> startRemove(Long id, String person) {
        WorkTicket ticket = lockTicket(id);
        if (!WorkTicket.STATUS_PERMITTED.equals(ticket.getStatus())
                || !WorkTicket.PHASE_WORKING.equals(ticket.getPermitPhase())) {
            throw new BizException("只有作业中的已许可工作票才能通知拆除安措");
        }
        ticket.setPermitPhase(WorkTicket.PHASE_DEMOLISHING);
        workTicketRepository.saveAndFlush(ticket);
        measureService.markToRemove(id, person);
        recordEvent(id, WorkTicketEvent.TYPE_START_REMOVE, person,
                "现场作业完成，通知逐项拆除安措并执行关联恢复操作");
        return detail(id);
    }

    // ============================================================
    // 撤回许可（现场变更的唯一入口）
    // ============================================================

    @Transactional
    public Map<String, Object> withdrawPermit(Long id, String person, String reason) {
        WorkTicket ticket = lockTicket(id);
        if (!WorkTicket.STATUS_PERMITTED.equals(ticket.getStatus())) {
            throw new BizException("只有已许可的工作票才能撤回许可（当前为「"
                    + Labels.workStatus(ticket.getStatus()) + "」）");
        }
        // 已确认痕迹保留：许可人/许可时间不清空，只回退票面状态与子阶段
        ticket.setStatus(WorkTicket.STATUS_ISSUED);
        ticket.setPermitPhase(null);
        workTicketRepository.saveAndFlush(ticket);
        measureService.reactivateAfterWithdraw(id, person, reason);
        recordEvent(id, WorkTicketEvent.TYPE_WITHDRAW, person,
                "撤回许可：" + reason + "。原许可与安措装拆痕迹保留，新增/变更项重新待执行");
        return detail(id);
    }

    /** 撤回许可后现场变更：原措施 VOIDED 留痕，新版本回 PENDING */
    @Transactional
    public Map<String, Object> changeMeasure(Long id, Long measureId, MeasureItemRequest item,
                                             String person, String reason) {
        WorkTicket ticket = lockTicket(id);
        if (!WorkTicket.STATUS_ISSUED.equals(ticket.getStatus())) {
            throw new BizException("现场变更安措必须先撤回许可（票面回到已签发）；已许可状态下不得直接删改");
        }
        if (reason == null || reason.isBlank()) {
            throw new BizException("请填写现场变更原因");
        }
        measureService.changeMeasure(id, measureId, item, person, reason);
        return detail(id);
    }

    /** 撤回许可后新增措施 */
    @Transactional
    public Map<String, Object> appendMeasure(Long id, MeasureItemRequest item, String person, String reason) {
        WorkTicket ticket = lockTicket(id);
        if (!WorkTicket.STATUS_ISSUED.equals(ticket.getStatus())) {
            throw new BizException("只有撤回许可（票面回到已签发）后才能补充新增安措");
        }
        if (reason == null || reason.isBlank()) {
            throw new BizException("请填写新增安措的现场原因");
        }
        measureService.appendMeasure(id, item, person, reason);
        return detail(id);
    }

    /** 作废工作票 */
    @Transactional
    public Map<String, Object> cancel(Long id, String person, String reason) {
        WorkTicket ticket = lockTicket(id);
        if (WorkTicket.STATUS_TERMINATED.equals(ticket.getStatus())) {
            throw new BizException("已终结的工作票不能作废");
        }
        if (WorkTicket.STATUS_CANCELED.equals(ticket.getStatus())) {
            throw new BizException("工作票已作废，请勿重复操作");
        }
        boolean wasPermitted = WorkTicket.STATUS_PERMITTED.equals(ticket.getStatus());
        ticket.setStatus(WorkTicket.STATUS_CANCELED);
        ticket.setPermitPhase(null);
        workTicketRepository.saveAndFlush(ticket);
        measureService.voidAllByCancel(id, person, reason == null ? "" : reason);
        recordEvent(id, WorkTicketEvent.TYPE_CANCEL, person,
                "工作票作废" + (wasPermitted ? "（原已许可，安措装拆记录保留备查）" : "")
                        + (reason == null || reason.isBlank() ? "" : "：" + reason));
        return detail(id);
    }

    // ============================================================
    // 三审签字
    // ============================================================

    @Transactional
    public Map<String, Object> sign(Long id, String stage, String person) {
        WorkTicket ticket = lockTicket(id);
        Bay bay = ticket.getBayId() == null ? null
                : bayRepository.findById(ticket.getBayId()).orElse(null);

        // 已作废 / 已终结的票冻结，任何环节签字都直接拒绝（并发后提交者也在此看到最新结果）
        if (WorkTicket.STATUS_CANCELED.equals(ticket.getStatus())) {
            throw new BizException("工作票已作废，不能再进行签发/许可/终结签字（历史痕迹保留可查）");
        }
        if (WorkTicket.STATUS_TERMINATED.equals(ticket.getStatus())
                && !WorkTicket.STAGE_TERMINATE.equals(stage)) {
            throw new BizException("工作票已终结，不能再签字");
        }

        switch (stage) {
            case WorkTicket.STAGE_ISSUE -> {
                if (!WorkTicket.STATUS_DRAFT.equals(ticket.getStatus())) {
                    throw new BizException("该票已签发，不能再由签发人签字");
                }
                ticket.setIssuer(person);
                ticket.setIssueTime(LocalDateTime.now());
                ticket.setStatus(WorkTicket.STATUS_ISSUED);
                workTicketRepository.saveAndFlush(ticket);
                recordEvent(id, WorkTicketEvent.TYPE_ISSUE, person, "工作票签发");
            }
            case WorkTicket.STAGE_PERMIT -> {
                if (WorkTicket.STATUS_PERMITTED.equals(ticket.getStatus())) {
                    // 并发许可：后提交者直接看到「已许可」的最新结果
                    throw new BizException("该工作票已由 " + ticket.getPermittor()
                            + " 于 " + ticket.getPermitTime() + " 许可，本次提交未重复生效");
                }
                if (!WorkTicket.STATUS_ISSUED.equals(ticket.getStatus())) {
                    throw new BizException("尚未经签发人签发（或许可已被撤回），许可人不能签字");
                }

                // ★★ 许可闸门：工作票侧安措 + 关联操作票侧步骤，两侧同时核验 ★★
                SafetyMeasureService.GateResult gate = measureService.evaluatePermitGate(id);
                if (!gate.pass) {
                    throw new BizException(buildPermitBlockMessage(gate));
                }

                ticket.setPermittor(person);
                ticket.setPermitTime(LocalDateTime.now());
                ticket.setStatus(WorkTicket.STATUS_PERMITTED);
                ticket.setPermitPhase(WorkTicket.PHASE_WORKING);
                workTicketRepository.saveAndFlush(ticket);
                recordEvent(id, WorkTicketEvent.TYPE_PERMIT, person,
                        "许可开工：安措已逐项装设确认，关联操作票对应步骤均已执行");

                if (bay != null && !Bay.STATUS_MAINTENANCE.equals(bay.getStatus())) {
                    bay.setStatus(Bay.STATUS_MAINTENANCE);
                    bayRepository.save(bay);
                }
            }
            case WorkTicket.STAGE_TERMINATE -> {
                if (WorkTicket.STATUS_TERMINATED.equals(ticket.getStatus())) {
                    throw new BizException("该工作票已终结，请勿重复签字");
                }
                if (!WorkTicket.STATUS_PERMITTED.equals(ticket.getStatus())) {
                    throw new BizException("尚未许可开工，终结人不能先签字");
                }
                if (!WorkTicket.PHASE_DEMOLISHING.equals(ticket.getPermitPhase())) {
                    throw new BizException("现场作业尚未通知拆除安措，请先由许可人通知拆除，待安措与恢复操作全部完成后再终结");
                }

                // ★★ 终结闸门：需拆除措施全部拆除 + 关联恢复操作票步骤全部 DONE ★★
                SafetyMeasureService.GateResult gate = measureService.evaluateTerminateGate(id);
                if (!gate.pass) {
                    throw new BizException(buildTerminateBlockMessage(gate));
                }

                List<Defect> overdue = defectRepository.findByBayIdOrderByDueDateAsc(ticket.getBayId()).stream()
                        .filter(d -> !Defect.STATUS_ACCEPTED.equals(d.getStatus()))
                        .filter(d -> d.getDueDate() != null && LocalDate.now().isAfter(d.getDueDate()))
                        .toList();
                if (!overdue.isEmpty()) {
                    Defect worst = overdue.get(0);
                    throw new BizException("该间隔尚有 " + overdue.size() + " 条超期未消缺（如 " + worst.getDefectNo()
                            + " 已超期 " + java.time.temporal.ChronoUnit.DAYS.between(worst.getDueDate(), LocalDate.now())
                            + " 天），消缺闭环前不允许终结工作票");
                }
                ticket.setTerminator(person);
                ticket.setTerminateTime(LocalDateTime.now());
                ticket.setStatus(WorkTicket.STATUS_TERMINATED);
                ticket.setPermitPhase(null);
                workTicketRepository.saveAndFlush(ticket);
                recordEvent(id, WorkTicketEvent.TYPE_TERMINATE, person,
                        "工作票终结：需拆除安措已全部拆除，关联操作票恢复步骤均已执行");
            }
            default -> throw new BizException("未知的签字环节：" + stage);
        }

        ensureDifferentPersons(ticket);
        return detail(id);
    }

    /** 许可闸门被拦截时的中文提示：逐项指出卡在哪里、属于哪一侧 */
    private String buildPermitBlockMessage(SafetyMeasureService.GateResult gate) {
        StringBuilder sb = new StringBuilder("禁止许可：安全措施装设与关联倒闸操作未同时完成，共 ")
                .append(gate.measureBlockers.size()).append(" 项卡点：\n");
        int i = 1;
        for (SafetyMeasureService.Blocker b : gate.measureBlockers) {
            sb.append(i++).append(". ").append(b.source).append(" —— ").append(b.reason).append("\n");
        }
        sb.append("请先完成上述安措装设确认及同一间隔关联操作票的对应步骤，再发起许可。");
        return sb.toString();
    }

    private String buildTerminateBlockMessage(SafetyMeasureService.GateResult gate) {
        StringBuilder sb = new StringBuilder("禁止终结：安措拆除与关联恢复操作未全部完成，共 ")
                .append(gate.measureBlockers.size()).append(" 项卡点：\n");
        int i = 1;
        for (SafetyMeasureService.Blocker b : gate.measureBlockers) {
            sb.append(i++).append(". ").append(b.source).append(" —— ").append(b.reason).append("\n");
        }
        sb.append("请逐项拆除剩余安措，并完成关联操作票的恢复步骤后再终结。");
        return sb.toString();
    }

    /** 取行级悲观锁，保证所有票面推进动作串行 */
    private WorkTicket lockTicket(Long id) {
        return workTicketRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("工作票不存在"));
    }

    private void recordEvent(Long ticketId, String type, String person, String detail) {
        WorkTicketEvent e = new WorkTicketEvent();
        e.setTicketId(ticketId);
        e.setEventType(type);
        e.setOperatorName(person);
        e.setEventTime(LocalDateTime.now());
        e.setDetail(detail);
        eventRepository.save(e);
    }

    private void ensureDifferentPersons(WorkTicket ticket) {
        String issuer = ticket.getIssuer();
        String permittor = ticket.getPermittor();
        String terminator = ticket.getTerminator();
        if (notBlank(issuer) && issuer.equals(permittor)) {
            throw new BizException("签发人与许可人不得为同一人（" + issuer + "）");
        }
        if (notBlank(issuer) && issuer.equals(terminator)) {
            throw new BizException("签发人与终结人不得为同一人（" + issuer + "）");
        }
        if (notBlank(permittor) && permittor.equals(terminator)) {
            throw new BizException("许可人与终结人不得为同一人（" + permittor + "）");
        }
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * 【手写 JPQL】两票状态统计（指定日期的工作票票面状态分布）。
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> statusStats(LocalDate date) {
        LocalDate day = date == null ? LocalDate.now() : date;
        List<TicketStatusCount> rows = workTicketRepository.statByStatus(day, day);
        List<Map<String, Object>> result = new ArrayList<>();
        for (TicketStatusCount row : rows) {
            Map<String, Object> item = new HashMap<>();
            item.put("status", row.getStatus());
            item.put("statusName", Labels.workStatus(row.getStatus()));
            item.put("count", row.getCount());
            result.add(item);
        }
        return result;
    }

    // ============================================================
    // 视图
    // ============================================================

    private Map<String, Object> toListMap(WorkTicket t, Bay bay) {
        Map<String, Object> map = baseMap(t, bay);
        Map<String, Long> mc = measureService.statusCount(t.getId());
        map.put("measureTotal", mc.values().stream().mapToLong(Long::longValue).sum());
        map.put("pendingCount", mc.getOrDefault(SafetyMeasure.STATUS_PENDING, 0L));
        map.put("installedCount", mc.getOrDefault(SafetyMeasure.STATUS_INSTALLED, 0L));
        map.put("toRemoveCount", mc.getOrDefault(SafetyMeasure.STATUS_TO_REMOVE, 0L));
        map.put("removedCount", mc.getOrDefault(SafetyMeasure.STATUS_REMOVED, 0L));
        return map;
    }

    private Map<String, Object> toDetailMap(WorkTicket t, Bay bay) {
        Map<String, Object> map = baseMap(t, bay);
        map.put("measures", measureService.measureViews(t.getId()));
        map.put("measureLogs", measureService.logViews(t.getId()));
        map.put("events", eventViews(t.getId()));

        SafetyMeasureService.GateResult permitGate = measureService.evaluatePermitGate(t.getId());
        SafetyMeasureService.GateResult terminateGate = measureService.evaluateTerminateGate(t.getId());
        map.put("permitReady", permitGate.pass);
        map.put("permitBlockers", permitGate.messages());
        map.put("terminateReady", terminateGate.pass);
        map.put("terminateBlockers", terminateGate.messages());
        map.put("permitPhase", t.getPermitPhase());
        map.put("permitPhaseName", t.getPermitPhase() == null ? "" : Labels.permitPhase(t.getPermitPhase()));

        // 下一步可做什么（撤回 / 作废 / 部分失败后重新进入，都靠这里给出真实指引）
        map.put("canInstall", WorkTicket.STATUS_DRAFT.equals(t.getStatus())
                || WorkTicket.STATUS_ISSUED.equals(t.getStatus()));
        map.put("canWithdraw", WorkTicket.STATUS_PERMITTED.equals(t.getStatus()));
        map.put("canStartRemove", WorkTicket.STATUS_PERMITTED.equals(t.getStatus())
                && WorkTicket.PHASE_WORKING.equals(t.getPermitPhase()));
        map.put("canRemove", WorkTicket.STATUS_PERMITTED.equals(t.getStatus())
                && WorkTicket.PHASE_DEMOLISHING.equals(t.getPermitPhase()));
        map.put("canChange", WorkTicket.STATUS_ISSUED.equals(t.getStatus()));
        map.put("canCancel", !WorkTicket.STATUS_TERMINATED.equals(t.getStatus())
                && !WorkTicket.STATUS_CANCELED.equals(t.getStatus()));
        return map;
    }

    private List<Map<String, Object>> eventViews(Long ticketId) {
        List<Map<String, Object>> views = new ArrayList<>();
        for (WorkTicketEvent e : eventRepository.findByTicketIdOrderByEventTimeAscIdAsc(ticketId)) {
            Map<String, Object> v = new HashMap<>();
            v.put("id", e.getId());
            v.put("eventType", e.getEventType());
            v.put("eventTypeName", Labels.ticketEvent(e.getEventType()));
            v.put("operatorName", e.getOperatorName());
            v.put("eventTime", e.getEventTime());
            v.put("detail", e.getDetail());
            views.add(v);
        }
        return views;
    }

    private Map<String, Object> baseMap(WorkTicket t, Bay bay) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", t.getId());
        map.put("ticketNo", t.getTicketNo());
        map.put("bayId", t.getBayId());
        map.put("bayName", bay == null ? "" : bay.getName());
        map.put("bayStatus", bay == null ? "" : bay.getStatus());
        map.put("bayStatusName", bay == null ? "" : Labels.bayStatus(bay.getStatus()));
        map.put("voltageLevel", bay == null ? "" : bay.getVoltageLevel());
        map.put("issueDate", t.getIssueDate());
        map.put("workLeader", t.getWorkLeader());
        map.put("teamMembers", t.getTeamMembers());
        map.put("workContent", t.getWorkContent());
        map.put("powerCutRange", t.getPowerCutRange());
        map.put("safetyMeasures", t.getSafetyMeasures());
        map.put("planStartTime", t.getPlanStartTime());
        map.put("planEndTime", t.getPlanEndTime());
        map.put("issuer", t.getIssuer());
        map.put("permittor", t.getPermittor());
        map.put("terminator", t.getTerminator());
        map.put("issueTime", t.getIssueTime());
        map.put("permitTime", t.getPermitTime());
        map.put("terminateTime", t.getTerminateTime());
        map.put("status", t.getStatus());
        map.put("statusName", Labels.workStatus(t.getStatus()));
        map.put("remark", t.getRemark());

        String nextStage;
        String waiting;
        switch (t.getStatus()) {
            case WorkTicket.STATUS_DRAFT -> {
                nextStage = WorkTicket.STAGE_ISSUE;
                waiting = "待签发人签字（安措可继续逐项装设确认）";
            }
            case WorkTicket.STATUS_ISSUED -> {
                nextStage = WorkTicket.STAGE_PERMIT;
                waiting = "待安措装设与关联操作步骤齐备后许可";
            }
            case WorkTicket.STATUS_PERMITTED -> {
                if (WorkTicket.PHASE_DEMOLISHING.equals(t.getPermitPhase())) {
                    nextStage = WorkTicket.STAGE_TERMINATE;
                    waiting = "安措拆除恢复中，拆完并核对恢复步骤后终结";
                } else {
                    nextStage = "";
                    waiting = "已许可开工，作业中";
                }
            }
            case WorkTicket.STATUS_CANCELED -> {
                nextStage = "";
                waiting = "已作废（历史痕迹保留）";
            }
            default -> {
                nextStage = "";
                waiting = "三审完成";
            }
        }
        map.put("nextStage", nextStage);
        map.put("waitingText", waiting);
        map.put("signedCount", countSigned(t));
        return map;
    }

    private int countSigned(WorkTicket t) {
        int count = 0;
        if (notBlank(t.getIssuer())) {
            count++;
        }
        if (notBlank(t.getPermittor())) {
            count++;
        }
        if (notBlank(t.getTerminator())) {
            count++;
        }
        return count;
    }
}
