package com.media.bus.stop.repository;

import com.media.bus.stop.entity.Stop;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StopRepository extends JpaRepository<Stop, UUID> {

    Optional<Stop> findByStopId(String stopId);

    boolean existsByStopId(String stopId);

    List<Stop> findByStopIdIn(Collection<String> stopIds);

    // idx_stop_name 인덱스 활용 — 'text%' 전방 일치만 허용 (후방/중간 LIKE는 인덱스 스캔 불가)
    Page<Stop> findByStopNameStartingWith(String stopName, Pageable pageable);
}