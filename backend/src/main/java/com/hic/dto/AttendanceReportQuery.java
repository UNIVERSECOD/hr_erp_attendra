package com.hic.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;

@Data
public class AttendanceReportQuery {
    public enum View { DETAILS, DAILY, MONTHLY, YEARLY }
    private View view = View.DETAILS;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate start;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate end;
    private String name;
    private String fin;
    private Long timetableId;
    private Long areaId;
    private Long departmentId;
    private Long positionId;
    private String status;
    private int page = 0;
    private int size = 20;
}
