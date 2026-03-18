package com.media.bus.stop.repository;

import com.media.bus.stop.entity.Stop;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StopRepository extends JpaRepository<Stop, UUID> {
}