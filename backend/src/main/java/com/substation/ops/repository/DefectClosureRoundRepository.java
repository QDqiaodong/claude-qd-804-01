package com.substation.ops.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.substation.ops.domain.DefectClosureRound;

public interface DefectClosureRoundRepository extends JpaRepository<DefectClosureRound, Long> {

    List<DefectClosureRound> findByDefectIdOrderByRoundNoAscIdAsc(Long defectId);

    List<DefectClosureRound> findByDefectIdInOrderByDefectIdAscRoundNoAscIdAsc(List<Long> defectIds);

    long countByDefectId(Long defectId);
}
