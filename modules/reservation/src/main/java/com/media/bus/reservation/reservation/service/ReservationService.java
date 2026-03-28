package com.media.bus.reservation.reservation.service;

import com.media.bus.reservation.reservation.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;

    // TODO : 추후 구현 필요
    public void existsReservation() {

    }
}
