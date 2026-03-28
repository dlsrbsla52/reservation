package com.media.bus.reservation.contract.repository;

import com.media.bus.reservation.contract.entity.Contract;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BaseEntityRepository extends JpaRepository<Contract, UUID> {
}