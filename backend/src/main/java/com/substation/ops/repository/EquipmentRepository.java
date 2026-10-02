package com.substation.ops.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.substation.ops.domain.Equipment;

public interface EquipmentRepository extends JpaRepository<Equipment, Long> {

    List<Equipment> findByBayIdOrderByIdAsc(Long bayId);
}
