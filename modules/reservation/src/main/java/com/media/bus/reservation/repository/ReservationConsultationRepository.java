package com.media.bus.reservation.repository;

import com.media.bus.reservation.entity.ReservationConsultation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReservationConsultationRepository extends JpaRepository<ReservationConsultation, UUID> {
}