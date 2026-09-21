package com.hic.util;

import com.hic.exception.ExportException;
import com.hic.model.DailyAttendanceSummary;
import com.hic.model.Employee;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Component
public class ExcelExportUtil {

    public byte[] exportAttendanceReport(List<com.hic.dto.ReportDTO.AttendanceReportDTO> data,
                                          java.time.LocalDate start, java.time.LocalDate end) {
        try (Workbook workbook = createWorkbook()) {
            Sheet sheet = workbook.createSheet("Attendance Report");
            CellStyle headerStyle = createHeaderStyle(workbook);
            Row header = sheet.createRow(0);
            String[] headers = {"Employee ID", "Employee Name", "Present Days", "Absent Days", "Late Days", "Total Hours"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 5000);
            }
            int rowNum = 1;
            for (com.hic.dto.ReportDTO.AttendanceReportDTO item : data) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(item.getEmployeeId() != null ? item.getEmployeeId().toString() : "");
                row.createCell(1).setCellValue(nullSafe(item.getEmployeeName()));
                row.createCell(2).setCellValue(item.getPresentDays() != null ? item.getPresentDays() : 0);
                row.createCell(3).setCellValue(item.getAbsentDays() != null ? item.getAbsentDays() : 0);
                row.createCell(4).setCellValue(item.getLateDays() != null ? item.getLateDays() : 0);
                row.createCell(5).setCellValue(item.getTotalHoursWorked() != null ? item.getTotalHoursWorked() : 0);
            }
            return toByteArray(workbook);
        } catch (IOException e) {
            throw new ExportException("Failed to generate attendance report Excel", e);
        }
    }

    public byte[] exportEmployeeSummary(List<com.hic.dto.ReportDTO.EmployeeSummaryDTO> data) {
        try (Workbook workbook = createWorkbook()) {
            Sheet sheet = workbook.createSheet("Employees");
            CellStyle headerStyle = createHeaderStyle(workbook);
            Row header = sheet.createRow(0);
            String[] headers = {"Employee ID", "Employee Name", "Department", "Status"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 5000);
            }
            int rowNum = 1;
            for (com.hic.dto.ReportDTO.EmployeeSummaryDTO item : data) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(item.getEmployeeId() != null ? item.getEmployeeId().toString() : "");
                row.createCell(1).setCellValue(nullSafe(item.getEmployeeName()));
                row.createCell(2).setCellValue(nullSafe(item.getDepartmentName()));
                row.createCell(3).setCellValue(nullSafe(item.getEmploymentStatus()));
            }
            return toByteArray(workbook);
        } catch (IOException e) {
            throw new ExportException("Failed to generate employee summary Excel", e);
        }
    }

    public Workbook createWorkbook() {
        return new XSSFWorkbook();
    }

    public byte[] writeEmployees(List<Employee> employees) {
        try (Workbook workbook = createWorkbook()) {
            Sheet sheet = workbook.createSheet("Employees");

            // Header row
            Row header = sheet.createRow(0);
            CellStyle headerStyle = createHeaderStyle(workbook);
            String[] headers = {"ID", "Employee ID", "First Name", "Last Name", "Email",
                    "Mobile Phone", "Department ID", "Position ID", "Hire Date",
                    "Employment Status", "FIN Number"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 4000);
            }

            // Data rows
            int rowNum = 1;
            for (Employee emp : employees) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(emp.getId() != null ? emp.getId() : 0);
                row.createCell(1).setCellValue(nullSafe(emp.getEmployeeId()));
                row.createCell(2).setCellValue(nullSafe(emp.getFirstName()));
                row.createCell(3).setCellValue(nullSafe(emp.getLastName()));
                row.createCell(4).setCellValue(nullSafe(emp.getEmail()));
                row.createCell(5).setCellValue(nullSafe(emp.getMobilePhone()));
                row.createCell(6).setCellValue(emp.getDepartmentId() != null ? emp.getDepartmentId() : 0);
                row.createCell(7).setCellValue(emp.getPositionId() != null ? emp.getPositionId() : 0);
                row.createCell(8).setCellValue(emp.getHireDate() != null ? emp.getHireDate().toString() : "");
                row.createCell(9).setCellValue(emp.getEmploymentStatus() != null ? emp.getEmploymentStatus().name() : "");
                row.createCell(10).setCellValue(nullSafe(emp.getFinNumber()));
            }

            return toByteArray(workbook);
        } catch (IOException e) {
            throw new ExportException("Failed to generate employee Excel report", e);
        }
    }

    public byte[] writeAttendance(List<DailyAttendanceSummary> summaries) {
        try (Workbook workbook = createWorkbook()) {
            Sheet sheet = workbook.createSheet("Attendance");

            Row header = sheet.createRow(0);
            CellStyle headerStyle = createHeaderStyle(workbook);
            String[] headers = {"Employee ID", "Date", "Check In", "Check Out",
                    "Hours Worked", "Status", "Is Holiday", "Is Leave"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 4000);
            }

            int rowNum = 1;
            for (DailyAttendanceSummary s : summaries) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(s.getEmployeeId() != null ? s.getEmployeeId() : 0);
                row.createCell(1).setCellValue(s.getAttendanceDate() != null ? s.getAttendanceDate().toString() : "");
                row.createCell(2).setCellValue(s.getCheckInTime() != null ? s.getCheckInTime().toString() : "");
                row.createCell(3).setCellValue(s.getCheckOutTime() != null ? s.getCheckOutTime().toString() : "");
                row.createCell(4).setCellValue(s.getHoursWorked() != null ? s.getHoursWorked() : 0);
                row.createCell(5).setCellValue(s.getAttendanceStatus() != null ? s.getAttendanceStatus().name() : "");
                row.createCell(6).setCellValue(Boolean.TRUE.equals(s.getIsHoliday()) ? "Yes" : "No");
                row.createCell(7).setCellValue(Boolean.TRUE.equals(s.getIsLeave()) ? "Yes" : "No");
            }

            return toByteArray(workbook);
        } catch (IOException e) {
            throw new ExportException("Failed to generate attendance Excel report", e);
        }
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private byte[] toByteArray(Workbook workbook) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        return out.toByteArray();
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }
}
