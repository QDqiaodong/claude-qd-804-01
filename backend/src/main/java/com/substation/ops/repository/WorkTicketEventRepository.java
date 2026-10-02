package com.substation.ops.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.substation.ops.domain.WorkTicketEvent;

public interface WorkTicketEventRepository extends JpaRepository<WorkTicketEvent, Long> {

    List<WorkTicketEvent> findByTicketIdOrderByEventTimeAscIdAsc(Long ticketId);
}
