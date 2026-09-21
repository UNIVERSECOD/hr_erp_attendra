package com.hic.repository;

import com.hic.model.AccessCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccessCardRepository extends JpaRepository<AccessCard, Long> {
    List<AccessCard> findByEmployeeId(Long employeeId);
    Optional<AccessCard> findByCardNumber(String cardNumber);
}
