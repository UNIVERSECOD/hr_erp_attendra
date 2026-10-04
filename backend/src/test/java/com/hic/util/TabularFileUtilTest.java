package com.hic.util;

import com.hic.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.ByteArrayInputStream;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TabularFileUtilTest {

    private final TabularFileUtil fileUtil = new TabularFileUtil();

    @Test
    void readsSemicolonSeparatedUtf8Csv() {
        String csv = "\uFEFFDepartament adı;Təsvir;Ərazi\n"
                + "İnsan Resursları;Əməkdaş idarəetməsi;Baş ofis\n";
        MockMultipartFile file = new MockMultipartFile(
                "file", "departments.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        TabularFileUtil.ParsedTable table = fileUtil.read(file);

        assertThat(table.rows()).hasSize(1);
        assertThat(table.rows().get(0).rowNumber()).isEqualTo(2);
        assertThat(table.rows().get(0).get("Departament adı")).isEqualTo("İnsan Resursları");
        assertThat(table.rows().get(0).get("Ərazi")).isEqualTo("Baş ofis");
    }

    @Test
    void excelRoundTripKeepsHeadersAndValues() {
        byte[] data = fileUtil.writeExcel(
                "Employees",
                List.of("Ad", "Soyad", "FIN"),
                List.of(List.of("Leyla", "Əliyeva", "7ABC123")));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "employees.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                data);

        TabularFileUtil.ParsedTable table = fileUtil.read(file);

        assertThat(table.headers()).containsExactly("ad", "soyad", "fin");
        assertThat(table.rows()).hasSize(1);
        assertThat(table.rows().get(0).get("FIN")).isEqualTo("7ABC123");
    }

    @Test
    void templateHasRequiredColorsChoicesAndCanBeImportedWithItsNote() throws Exception {
        byte[] data = fileUtil.writeExcelTemplate("Employees", List.of("Ad", "Qrafik", "Qeyd"),
                Set.of("Ad", "Qrafik"), Map.of("Qrafik", List.of("Standart", "Gecə")),
                List.of(List.of("Ərazi", "Departament", "Vəzifə")));
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(data))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo(TabularFileUtil.TEMPLATE_NOTE);
            assertThat(workbook.getFontAt(sheet.getRow(1).getCell(0).getCellStyle().getFontIndex()).getColor())
                    .isEqualTo(IndexedColors.RED.getIndex());
            assertThat(workbook.getFontAt(sheet.getRow(1).getCell(2).getCellStyle().getFontIndex()).getColor())
                    .isNotEqualTo(IndexedColors.RED.getIndex());
            assertThat(sheet.getDataValidations()).hasSize(1);
            assertThat(sheet.getDataValidations().get(0).getRegions().getCellRangeAddresses()[0].getFirstRow())
                    .isEqualTo(2);
            assertThat(workbook.getSheet("Siyahılar").getRow(1).getCell(0).getStringCellValue()).isEqualTo("Standart");
            var row = sheet.createRow(2);
            row.createCell(0).setCellValue("Leyla");
            row.createCell(1).setCellValue("Standart");
            try (var output = new java.io.ByteArrayOutputStream()) {
                workbook.write(output);
                var table = fileUtil.read(new MockMultipartFile("file", "template.xlsx", "application/octet-stream", output.toByteArray()));
                assertThat(table.headers()).containsExactly("ad", "qrafik", "qeyd");
                assertThat(table.rows()).hasSize(1);
                assertThat(table.rows().get(0).rowNumber()).isEqualTo(3);
                assertThat(table.rows().get(0).get("Ad")).isEqualTo("Leyla");
            }
        }
    }

    @Test
    void rejectsUnsupportedFiles() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "employees.txt", "text/plain", "Ad".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> fileUtil.read(file))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("CSV");
    }
}
