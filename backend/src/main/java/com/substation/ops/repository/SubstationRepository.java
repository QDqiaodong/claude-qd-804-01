package com.substation.ops.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.substation.ops.domain.Substation;

public interface SubstationRepository extends JpaRepository<Substation, Long> {
}
