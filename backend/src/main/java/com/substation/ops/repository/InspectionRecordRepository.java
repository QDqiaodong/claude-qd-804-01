package com.substation.ops.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.substation.ops.domain.InspectionRecord;

public interface InspectionRecordRepository extends JpaRepository<InspectionRecord, Long> {

    /** 某天的巡检记录（按检查时间倒序），JPQL 里先用范围过滤避免时区对齐问题 */
    @Query("select r from InspectionRecord r where r.checkTime >= :start and r.checkTime < :end order by r.checkTime desc")
    List<InspectionRecord> findByDay(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    List<InspectionRecord> findByBayIdOrderByCheckTimeDesc(Long bayId);
}
