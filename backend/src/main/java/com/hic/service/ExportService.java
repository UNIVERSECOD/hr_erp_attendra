package com.hic.service;

import com.hic.dto.ReportDTO;
import com.hic.exception.ExportException;
import com.hic.util.CsvExportUtil;
import com.hic.util.ExcelExportUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExportService {

    private final ReportService reportService;
    private final ExcelExportUtil excelExportUtil;
    private final CsvExportUtil csvExportUtil;

    public byte[] exportAttendanceExcel(LocalDate start, LocalDate end, Long branchId) {
        try {
            List<ReportDTO.AttendanceReportDTO> data = reportService.getAttendanceReport(start, end, branchId, null);
            return excelExportUtil.exportAttendanceReport(data, start, end);
        } catch (Exception e) {
            throw new ExportException("Failed to export attendance Excel: " + e.getMessage());
        }
    }

    public byte[] exportAttendanceCsv(LocalDate start, LocalDate end, Long branchId) {
        try {
            List<ReportDTO.AttendanceReportDTO> data = reportService.getAttendanceReport(start, end, branchId, null);
            return csvExportUtil.exportAttendanceReport(data);
        } catch (Exception e) {
            throw new ExportException("Failed to export attendance CSV: " + e.getMessage());
        }
    }

    public byte[] exportEmployeesExcel(Long branchId, Long departmentId) {
        try {
            List<ReportDTO.EmployeeSummaryDTO> data = reportService.getEmployeeSummary(branchId, departmentId);
            return excelExportUtil.exportEmployeeSummary(data);
        } catch (Exception e) {
            throw new ExportException("Failed to export employees Excel: " + e.getMessage());
        }
    }
}
