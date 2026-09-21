package com.hic.service;

import com.hic.exception.ResourceNotFoundException;
import com.hic.model.WorkSchedule;
import com.hic.repository.EmployeeRepository;
import com.hic.repository.WorkScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkScheduleService {

    private final WorkScheduleRepository workScheduleRepository;
    private final EmployeeRepository employeeRepository;

    public List<WorkSchedule> getByEmployee(Long employeeId) {
        return workScheduleRepository.findByEmployeeId(employeeId);
    }

    public WorkSchedule getById(Long id) {
        return workScheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("WorkSchedule", id));
    }

    @Transactional
    public WorkSchedule create(WorkSchedule schedule) {
        if (!employeeRepository.existsById(schedule.getEmployeeId())) {
            throw new ResourceNotFoundException("Employee", schedule.getEmployeeId());
        }
        return workScheduleRepository.save(schedule);
    }

    @Transactional
    public WorkSchedule update(Long id, WorkSchedule updated) {
        WorkSchedule existing = workScheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("WorkSchedule", id));
        updated.setId(existing.getId());
        return workScheduleRepository.save(updated);
    }

    @Transactional
    public void delete(Long id) {
        if (!workScheduleRepository.existsById(id)) {
            throw new ResourceNotFoundException("WorkSchedule", id);
        }
        workScheduleRepository.deleteById(id);
    }
}
