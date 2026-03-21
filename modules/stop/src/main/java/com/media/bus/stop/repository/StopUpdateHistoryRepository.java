package com.media.bus.stop.repository;

import com.media.bus.stop.entity.StopUpdateHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StopUpdateHistoryRepository extends JpaRepository<StopUpdateHistory, UUID> {
}