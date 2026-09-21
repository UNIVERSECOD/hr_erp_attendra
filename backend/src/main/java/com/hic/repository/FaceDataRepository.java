package com.hic.repository;

import com.hic.model.FaceData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FaceDataRepository extends JpaRepository<FaceData, Long> {
    List<FaceData> findByEmployeeId(Long employeeId);
    Optional<FaceData> findTopByEmployeeIdOrderByCreatedAtDesc(Long employeeId);
    Optional<FaceData> findByFaceId(String faceId);
}
