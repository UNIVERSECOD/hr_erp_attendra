package com.hic.controller;

import com.hic.dto.IsapiUserInfoRecordDTO;
import com.hic.service.IsapiEmployeeAccessEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class IsapiAccessControlController {
    private final IsapiEmployeeAccessEventService isapiEmployeeAccessEventService;

    @PostMapping({"/api/isapi/access-control/user-info/record", "/ISAPI/AccessControl/UserInfo/Record"})
    public ResponseEntity<Void> receiveUserInfoRecord(@RequestBody IsapiUserInfoRecordDTO payload) {
        isapiEmployeeAccessEventService.routeUserInfoRecord(payload);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}
