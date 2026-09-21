package com.abv.hrerpisapi.service;

import com.abv.hrerpisapi.dao.entity.DeviceCursorEntity;
import com.abv.hrerpisapi.dao.repository.DeviceCursorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceCursorService {

    private final DeviceCursorRepository deviceCursorRepository;

    @Transactional
    public DeviceCursorEntity resetCursor(Long deviceId) {
        DeviceCursorEntity cursor = deviceCursorRepository.findById(deviceId)
                .orElseGet(() -> {
                    DeviceCursorEntity c = new DeviceCursorEntity();
                    c.setDeviceId(deviceId);
                    return c;
                });

        cursor.setLastSerialNo(0L);
        cursor.setLastEventTime(null);
        DeviceCursorEntity saved = deviceCursorRepository.save(cursor);
        log.info("ActionLog.device.cursor.reset.ended deviceId={} lastSerialNo={} lastEventTime={}",
                saved.getDeviceId(), saved.getLastSerialNo(), saved.getLastEventTime());
        return saved;
    }
}
