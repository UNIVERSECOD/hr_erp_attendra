package com.hic.service;

import com.hic.exception.BadRequestException;
import com.hic.util.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class EmployeePermissionServiceTest {

    @Mock
    private com.hic.repository.EmployeePermissionRepository permissionRepository;
    @Mock
    private com.hic.repository.EmployeeRepository employeeRepository;
    @Mock
    private com.hic.repository.PermissionTypeRepository permissionTypeRepository;

    @InjectMocks
    private EmployeePermissionService service;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
        TenantContext.setUserId(2L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void grantPermission_rejectsExpiredPermission() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.grantPermission(1L, 1L, LocalDate.now().minusDays(5), LocalDate.now().minusDays(1), "note", null));
        assertEquals("Cannot assign expired permission", ex.getMessage());
    }

    @Test
    void grantPermission_rejectsInvalidDateRange() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.grantPermission(1L, 1L, LocalDate.now(), LocalDate.now().minusDays(1), "note", null));
        assertEquals("End date must be on or after start date", ex.getMessage());
    }
}
