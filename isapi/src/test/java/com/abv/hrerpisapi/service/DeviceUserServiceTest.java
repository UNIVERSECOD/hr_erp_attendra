package com.abv.hrerpisapi.service;

import com.abv.hrerpisapi.dao.entity.DeviceEntity;
import com.abv.hrerpisapi.dao.entity.DeviceUserEntity;
import com.abv.hrerpisapi.dao.repository.DeviceRepository;
import com.abv.hrerpisapi.dao.repository.DeviceUserRepository;
import com.abv.hrerpisapi.device.client.IsapiClient;
import com.abv.hrerpisapi.device.client.IsapiClient.UserOperationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceUserServiceTest {

    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private DeviceUserRepository deviceUserRepository;
    @Mock
    private IsapiClient isapiClient;

    @InjectMocks
    private DeviceUserService service;

    @Test
    void deleteByEmployeeNo_deletesUserAndLocalCache() throws Exception {
        DeviceEntity device = device(1L);
        DeviceUserEntity cachedUser = user(7L, "1234");
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        when(isapiClient.deleteDeviceUser(device, "1234"))
                .thenReturn(new UserOperationResult(true, 200, "ok"));
        when(deviceUserRepository.findByEmployeeNo("1234")).thenReturn(Optional.of(cachedUser));

        service.deleteDeviceUserByEmployeeNo(1L, "1234");

        verify(isapiClient).deleteDeviceUser(device, "1234");
        verify(isapiClient, never()).deleteFaceFromFDLib(device, "1234");
        verify(deviceUserRepository).delete(cachedUser);
    }

    @Test
    void deleteByEmployeeNo_acceptsAlreadyMissingUser() throws Exception {
        DeviceEntity device = device(1L);
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        when(isapiClient.deleteDeviceUser(device, "1234"))
                .thenReturn(new UserOperationResult(false, 404, "missing"));
        when(deviceUserRepository.findByEmployeeNo("1234")).thenReturn(Optional.empty());

        service.deleteDeviceUserByEmployeeNo(1L, "1234");

        verify(isapiClient).deleteDeviceUser(device, "1234");
        verify(isapiClient, never()).deleteFaceFromFDLib(device, "1234");
    }

    @Test
    void deleteByEmployeeNo_deviceFailureKeepsLocalCache() throws Exception {
        DeviceEntity device = device(1L);
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        when(isapiClient.deleteDeviceUser(device, "1234"))
                .thenReturn(new UserOperationResult(false, 500, "device error"));

        assertThatThrownBy(() -> service.deleteDeviceUserByEmployeeNo(1L, "1234"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> {
                    ResponseStatusException exception = (ResponseStatusException) error;
                    org.assertj.core.api.Assertions.assertThat(exception.getStatusCode())
                            .isEqualTo(HttpStatus.BAD_GATEWAY);
                });

        verify(isapiClient).deleteDeviceUser(device, "1234");
        verify(isapiClient, never()).deleteFaceFromFDLib(device, "1234");
        verify(deviceUserRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    private DeviceEntity device(Long id) {
        DeviceEntity device = new DeviceEntity();
        device.setId(id);
        return device;
    }

    private DeviceUserEntity user(Long id, String employeeNo) {
        DeviceUserEntity user = new DeviceUserEntity();
        user.setId(id);
        user.setEmployeeNo(employeeNo);
        return user;
    }
}
