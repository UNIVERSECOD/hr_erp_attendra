package com.hic.service;

import com.hic.dto.IsapiUserInfoRecordDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class DefaultIsapiEmployeeAccessEventService implements IsapiEmployeeAccessEventService {
    @Override
    public void routeUserInfoRecord(IsapiUserInfoRecordDTO record) {
        if (record == null || record.getUserInfo() == null) {
            return;
        }
        log.info("ISAPI user info record received for employeeNo={}", record.getUserInfo().getEmployeeNo());
    }
}
