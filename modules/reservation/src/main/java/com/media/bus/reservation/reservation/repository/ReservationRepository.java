package com.media.bus.reservation.reservation.repository;

import com.media.bus.reservation.reservation.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {
}