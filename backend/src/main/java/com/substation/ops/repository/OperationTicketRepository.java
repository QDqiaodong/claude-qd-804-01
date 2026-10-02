package com.substation.ops.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.substation.ops.domain.OperationTicket;
import com.substation.ops.dto.TimelineDay;

public interface OperationTicketRepository extends JpaRepository<OperationTicket, Long> {

    List<OperationTicket> findByIssueDateOrderByIdAsc(LocalDate issueDate);

    List<OperationTicket> findAllByOrderByIssueDateDescIdDesc();

    /** 行级悲观锁：执行步骤 / 登记失败时串行化，防止并发重复执行同一部 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from OperationTicket t where t.id = :id")
    Optional<OperationTicket> findByIdForUpdate(@Param("id") Long id);

    /** 顶部时间轴：按天统计操作票数量 */
    @Query("select new com.substation.ops.dto.TimelineDay(o.issueDate, 0L, count(o)) "
            + "from OperationTicket o "
            + "where o.issueDate between :from and :to "
            + "group by o.issueDate")
    List<TimelineDay> countByDay(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
