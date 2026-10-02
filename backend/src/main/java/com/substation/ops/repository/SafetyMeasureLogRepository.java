package com.substation.ops.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.substation.ops.domain.SafetyMeasureLog;

public interface SafetyMeasureLogRepository extends JpaRepository<SafetyMeasureLog, Long> {

    List<SafetyMeasureLog> findByTicketIdOrderByOperateTimeAscIdAsc(Long ticketId);

    List<SafetyMeasureLog> findByMeasureIdOrderByOperateTimeAscIdAsc(Long measureId);
}
