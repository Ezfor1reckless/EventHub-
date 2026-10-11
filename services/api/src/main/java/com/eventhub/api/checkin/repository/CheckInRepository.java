package com.eventhub.api.checkin.repository;

import com.eventhub.api.checkin.domain.CheckIn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CheckInRepository extends JpaRepository<CheckIn, Long> {

    boolean existsByRegistrationId(Long registrationId);

    Optional<CheckIn> findByRegistrationId(Long registrationId);

    long countByEventId(Long eventId);
}
