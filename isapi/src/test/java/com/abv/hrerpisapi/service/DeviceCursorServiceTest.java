package com.abv.hrerpisapi.service;

import com.abv.hrerpisapi.dao.entity.DeviceCursorEntity;
import com.abv.hrerpisapi.dao.repository.DeviceCursorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceCursorServiceTest {

    @Mock
    private DeviceCursorRepository deviceCursorRepository;

    @InjectMocks
    private DeviceCursorService deviceCursorService;

    @Test
    void resetCursor_existingCursor_resetsValues() {
        DeviceCursorEntity existing = new DeviceCursorEntity();
        existing.setDeviceId(1L);
        existing.setLastSerialNo(321L);
        existing.setLastEventTime(OffsetDateTime.now().minusHours(1));

        when(deviceCursorRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(deviceCursorRepository.save(any(DeviceCursorEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DeviceCursorEntity result = deviceCursorService.resetCursor(1L);

        ArgumentCaptor<DeviceCursorEntity> captor = ArgumentCaptor.forClass(DeviceCursorEntity.class);
        verify(deviceCursorRepository).save(captor.capture());
        DeviceCursorEntity saved = captor.getValue();
        assertThat(saved.getDeviceId()).isEqualTo(1L);
        assertThat(saved.getLastSerialNo()).isEqualTo(0L);
        assertThat(saved.getLastEventTime()).isNull();
        assertThat(result.getLastSerialNo()).isEqualTo(0L);
        assertThat(result.getLastEventTime()).isNull();
    }

    @Test
    void resetCursor_missingCursor_createsResetCursor() {
        when(deviceCursorRepository.findById(2L)).thenReturn(Optional.empty());
        when(deviceCursorRepository.save(any(DeviceCursorEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DeviceCursorEntity result = deviceCursorService.resetCursor(2L);

        assertThat(result.getDeviceId()).isEqualTo(2L);
        assertThat(result.getLastSerialNo()).isEqualTo(0L);
        assertThat(result.getLastEventTime()).isNull();
    }
}
