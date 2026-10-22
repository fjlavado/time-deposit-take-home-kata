package org.ikigaidigital.domain.model;

/** A zero-based page of deposits, ordered by ID. */
public record PageRequest(int page, int size) {
    public PageRequest {
        if (page < 0 || size < 1) {
            throw new IllegalArgumentException("Page must be non-negative and size must be positive");
        }
    }
}
