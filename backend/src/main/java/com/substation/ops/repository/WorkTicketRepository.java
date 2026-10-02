package com.substation.ops.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.query.Procedure;
import org.springframework.data.repository.query.Param;

import com.substation.ops.domain.WorkTicket;
import com.substation.ops.dto.TicketStatusCount;
import com.substation.ops.dto.TimelineDay;

public interface WorkTicketRepository extends JpaRepository<WorkTicket, Long> {

    List<WorkTicket> findByIssueDateOrderByIdAsc(LocalDate issueDate);

    List<WorkTicket> findByBayIdAndStatus(Long bayId, String status);

    /**
     * 行级悲观锁：签字、撤回、安措确认等所有会推进票面的动作都先取锁，
     * 两名值班员并发操作时串行化，后提交者拿到的一定是最新状态。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WorkTicket w where w.id = :id")
    Optional<WorkTicket> findByIdForUpdate(@Param("id") Long id);

    /**
     * 【存储过程】调用 sp_next_work_ticket_no 生成票号（单向出站参数返回票号字符串）。
     */
    @Procedure(name = "WorkTicket.nextTicketNo")
    String nextTicketNo(@Param("bayId") Long bayId, @Param("issueDate") String issueDate);

    /**
     * 【手写 JPQL 二】两票状态统计：指定日期区间内工作票各状态的票量。
     */
    @Query("select new com.substation.ops.dto.TicketStatusCount(w.status, count(w)) "
            + "from WorkTicket w "
            + "where w.issueDate between :from and :to "
            + "group by w.status "
            + "order by count(w) desc")
    List<TicketStatusCount> statByStatus(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** 顶部时间轴：按天统计工作票数量 */
    @Query("select new com.substation.ops.dto.TimelineDay(w.issueDate, count(w), 0L) "
            + "from WorkTicket w "
            + "where w.issueDate between :from and :to "
            + "group by w.issueDate")
    List<TimelineDay> countByDay(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
