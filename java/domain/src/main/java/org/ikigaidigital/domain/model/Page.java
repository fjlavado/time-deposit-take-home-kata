package org.ikigaidigital.domain.model;

import java.util.List;

public record Page<T>(List<T> content, int page, int size, long totalElements) {
    public Page {
        content = List.copyOf(content);
    }

    public long totalPages() {
        return Math.ceilDiv(totalElements, size);
    }

    public boolean hasNext() {
        return (long) page + 1 < totalPages();
    }
}
