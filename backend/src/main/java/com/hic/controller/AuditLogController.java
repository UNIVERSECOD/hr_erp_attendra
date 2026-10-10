package com.hic.controller;

import com.hic.dto.ApiResponse;
import com.hic.exception.BadRequestException;
import com.hic.model.AuditLog;
import com.hic.repository.AuditLogRepository;
import com.hic.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('HEAD_OFFICE_HR')")
public class AuditLogController {
    private final AuditLogRepository repository;

    @GetMapping
    public ApiResponse<Page<AuditLog>> list(@RequestParam(required=false) String username,
            @RequestParam(required=false) String action, @RequestParam(required=false) String entityType,
            @RequestParam(required=false) LocalDate start, @RequestParam(required=false) LocalDate end,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="25") int size) {
        Long tenant = TenantContext.getTenantId();
        if (tenant == null || page < 0 || size < 1 || size > 100 || (start != null && end != null && start.isAfter(end)))
            throw new BadRequestException("Jurnal filtrləri düzgün deyil.");
        Specification<AuditLog> filter = (root, query, cb) -> {
            var terms = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            terms.add(cb.equal(root.get("tenantId"), tenant));
            if (username != null && !username.isBlank()) terms.add(cb.equal(root.get("username"), username.trim()));
            if (action != null && !action.isBlank()) terms.add(cb.equal(root.get("action"), action));
            if (entityType != null && !entityType.isBlank()) terms.add(cb.equal(root.get("entityType"), entityType));
            if (start != null) terms.add(cb.greaterThanOrEqualTo(root.get("createdAt"), start.atStartOfDay()));
            if (end != null) terms.add(cb.lessThan(root.get("createdAt"), end.plusDays(1).atStartOfDay()));
            return cb.and(terms.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        return ApiResponse.success(repository.findAll(filter, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"))));
    }
}
