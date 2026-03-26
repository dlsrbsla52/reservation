package com.media.bus.stop.repository;

import com.media.bus.stop.entity.Stop;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StopRepository extends JpaRepository<Stop, UUID> {

    Optional<Stop> findByStopId(String stopId);

    List<Stop> findByStopIdIn(Collection<String> stopIds);

    // idx_stop_name 인덱스 활용 — 페이징 없이 전방 일치 조회
    List<Stop> findByStopNameStartingWith(String stopName);
}