package com.substation.ops.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.substation.ops.common.Labels;
import com.substation.ops.domain.Defect;
import com.substation.ops.dto.TicketStatusCount;
import com.substation.ops.dto.TimelineDay;
import com.substation.ops.repository.DefectRepository;
import com.substation.ops.repository.OperationTicketRepository;
import com.substation.ops.repository.WorkTicketRepository;

/**
 * 顶部时间轴与首页概览：把「某天」的票量、缺陷态势整理给前端。
 */
@Service
public class DashboardService {

    private final WorkTicketRepository workTicketRepository;
    private final OperationTicketRepository operationTicketRepository;
    private final DefectRepository defectRepository;

    public DashboardService(WorkTicketRepository workTicketRepository,
                            OperationTicketRepository operationTicketRepository,
                            DefectRepository defectRepository) {
        this.workTicketRepository = workTicketRepository;
        this.operationTicketRepository = operationTicketRepository;
        this.defectRepository = defectRepository;
    }

    /**
     * 时间轴：返回 [from, to] 每天的工作票 / 操作票票数（没有票的日子补 0 也要返回）。
     */
    @Transactional(readOnly = true)
    public List<TimelineDay> timeline(LocalDate from, LocalDate to) {
        Map<LocalDate, TimelineDay> buckets = new HashMap<>();
        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            buckets.put(cursor, new TimelineDay(cursor, 0L, 0L));
            cursor = cursor.plusDays(1);
        }
        merge(buckets, workTicketRepository.countByDay(from, to), true);
        merge(buckets, operationTicketRepository.countByDay(from, to), false);

        List<TimelineDay> result = new ArrayList<>(buckets.values());
        result.sort((a, b) -> a.getDate().compareTo(b.getDate()));
        return result;
    }

    private void merge(Map<LocalDate, TimelineDay> buckets, List<TimelineDay> rows, boolean work) {
        for (TimelineDay row : rows) {
            TimelineDay target = buckets.get(row.getDate());
            if (target == null) {
                continue;
            }
            if (work) {
                target.setWorkTicketCount(row.getWorkTicketCount());
            } else {
                target.setOperationTicketCount(row.getOperationTicketCount());
            }
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> overview(LocalDate date) {
        LocalDate day = date == null ? LocalDate.now() : date;
        Map<String, Object> map = new HashMap<>();
        map.put("date", day);
        map.put("workTicketCount", workTicketRepository.findByIssueDateOrderByIdAsc(day).size());
        map.put("operationTicketCount", operationTicketRepository.findByIssueDateOrderByIdAsc(day).size());

        List<Defect> open = defectRepository.findAll().stream()
                .filter(d -> !Defect.STATUS_ACCEPTED.equals(d.getStatus()))
                .toList();
        long overdue = open.stream()
                .filter(d -> d.getDueDate() != null && day.isAfter(d.getDueDate()))
                .count();
        map.put("openDefectCount", open.size());
        map.put("overdueDefectCount", overdue);
        map.put("fixRate", open.isEmpty() ? 0 : Math.round(100.0 * (open.size() - overdue) / open.size() * 10) / 10.0);

        List<TicketStatusCount> tickets = workTicketRepository.statByStatus(day, day);
        List<Map<String, Object>> statusList = new ArrayList<>();
        for (TicketStatusCount c : tickets) {
            Map<String, Object> item = new HashMap<>();
            item.put("status", c.getStatus());
            item.put("statusName", Labels.workStatus(c.getStatus()));
            item.put("count", c.getCount());
            statusList.add(item);
        }
        map.put("workTicketStatus", statusList);
        return map;
    }
}
