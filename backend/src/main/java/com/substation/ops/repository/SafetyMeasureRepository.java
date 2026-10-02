package com.substation.ops.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.substation.ops.domain.SafetyMeasure;

public interface SafetyMeasureRepository extends JpaRepository<SafetyMeasure, Long> {

    /** 一张票的全部措施（含变更留痕的 VOIDED 行），按票面序号、版本号排列 */
    List<SafetyMeasure> findByTicketIdOrderBySeqNoAscVersionNoAscIdAsc(Long ticketId);

    /** 参与闭环校验的现行措施（排除已变更作废的版本） */
    List<SafetyMeasure> findByTicketIdAndStatusNotOrderBySeqNoAscVersionNoAscIdAsc(Long ticketId, String excludedStatus);
}
