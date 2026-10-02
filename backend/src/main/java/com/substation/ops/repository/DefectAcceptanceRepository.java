package com.substation.ops.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.substation.ops.domain.DefectAcceptance;

public interface DefectAcceptanceRepository extends JpaRepository<DefectAcceptance, Long> {

    List<DefectAcceptance> findByDefectIdOrderByRoundNoAscIdAsc(Long defectId);

    /** 某缺陷当前轮次（验收结论尚未填写）的待验收记录；正常至多一条 */
    List<DefectAcceptance> findByDefectIdAndResultIsNullOrderByIdAsc(Long defectId);

    /** 某缺陷已经产生过的处理/验收轮次数 */
    long countByDefectId(Long defectId);
}
