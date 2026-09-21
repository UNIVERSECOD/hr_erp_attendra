package com.hic.util;

import com.hic.exception.ExportException;
import com.hic.model.DailyAttendanceSummary;
import com.hic.model.Employee;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class CsvExportUtil {

    public byte[] exportAttendanceReport(List<com.hic.dto.ReportDTO.AttendanceReportDTO> data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {
            writer.print('\uFEFF');
            writer.println("Employee ID,Employee Name,Present Days,Absent Days,Late Days,Total Hours");
            for (com.hic.dto.ReportDTO.AttendanceReportDTO item : data) {
                writer.printf("%s,%s,%s,%s,%s,%s%n",
                        nullSafe(item.getEmployeeId()),
                        csvEscape(item.getEmployeeName()),
                        nullSafe(item.getPresentDays()),
                        nullSafe(item.getAbsentDays()),
                        nullSafe(item.getLateDays()),
                        nullSafe(item.getTotalHoursWorked()));
            }
        }
        return out.toByteArray();
    }

    public byte[] writeEmployeesCsv(List<Employee> employees) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {
            // BOM for Excel UTF-8 compatibility
            writer.print('\uFEFF');
            writer.println("ID,Employee ID,First Name,Last Name,Email,Mobile Phone," +
                    "Department ID,Position ID,Hire Date,Employment Status,FIN Number");
            for (Employee emp : employees) {
                writer.printf("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s%n",
                        nullSafe(emp.getId()),
                        csvEscape(emp.getEmployeeId()),
                        csvEscape(emp.getFirstName()),
                        csvEscape(emp.getLastName()),
                        csvEscape(emp.getEmail()),
                        csvEscape(emp.getMobilePhone()),
                        nullSafe(emp.getDepartmentId()),
                        nullSafe(emp.getPositionId()),
                        emp.getHireDate() != null ? emp.getHireDate().toString() : "",
                        emp.getEmploymentStatus() != null ? emp.getEmploymentStatus().name() : "",
                        csvEscape(emp.getFinNumber())
                );
            }
        }
        return out.toByteArray();
    }

    public byte[] writeAttendanceCsv(List<DailyAttendanceSummary> summaries) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {
            writer.print('\uFEFF');
            writer.println("Employee ID,Date,Check In,Check Out,Hours Worked,Status,Is Holiday,Is Leave");
            for (DailyAttendanceSummary s : summaries) {
                writer.printf("%s,%s,%s,%s,%s,%s,%s,%s%n",
                        nullSafe(s.getEmployeeId()),
                        s.getAttendanceDate() != null ? s.getAttendanceDate().toString() : "",
                        s.getCheckInTime() != null ? s.getCheckInTime().toString() : "",
                        s.getCheckOutTime() != null ? s.getCheckOutTime().toString() : "",
                        s.getHoursWorked() != null ? s.getHoursWorked() : "",
                        s.getAttendanceStatus() != null ? s.getAttendanceStatus().name() : "",
                        Boolean.TRUE.equals(s.getIsHoliday()) ? "Yes" : "No",
                        Boolean.TRUE.equals(s.getIsLeave()) ? "Yes" : "No"
                );
            }
        }
        return out.toByteArray();
    }

    private String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String nullSafe(Object value) {
        return value != null ? value.toString() : "";
    }
}
