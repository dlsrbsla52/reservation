package com.media.bus.reservation.contract.repository;

import com.media.bus.reservation.contract.entity.ContractDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ContractDetailRepository extends JpaRepository<ContractDetail, UUID> {
}