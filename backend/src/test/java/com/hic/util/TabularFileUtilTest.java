package com.hic.util;

import com.hic.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

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
    void rejectsUnsupportedFiles() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "employees.txt", "text/plain", "Ad".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> fileUtil.read(file))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("CSV");
    }
}
