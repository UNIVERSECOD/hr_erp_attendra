package com.hic.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaginatedResponse<T> {
    private List<T> content;
    private long totalElements;
    private int totalPages;
    private int currentPage;
    private int pageSize;

    public static <T> PaginatedResponse<T> of(List<T> content, long totalElements,
                                               int totalPages, int currentPage, int pageSize) {
        return new PaginatedResponse<>(content, totalElements, totalPages, currentPage, pageSize);
    }
}
