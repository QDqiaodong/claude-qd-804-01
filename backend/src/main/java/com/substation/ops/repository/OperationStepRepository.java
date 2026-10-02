package com.substation.ops.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.substation.ops.domain.OperationStep;

public interface OperationStepRepository extends JpaRepository<OperationStep, Long> {

    List<OperationStep> findByTicketIdOrderByStepNoAsc(Long ticketId);

    List<OperationStep> findByTicketIdInOrderByTicketIdAscStepNoAsc(List<Long> ticketIds);
}
