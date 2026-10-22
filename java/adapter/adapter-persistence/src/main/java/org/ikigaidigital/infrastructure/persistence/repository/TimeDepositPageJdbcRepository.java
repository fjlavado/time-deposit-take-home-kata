package org.ikigaidigital.infrastructure.persistence.repository;

import org.ikigaidigital.infrastructure.persistence.jdbc.TimeDepositIdTable;
import org.springframework.data.repository.PagingAndSortingRepository;

public interface TimeDepositPageJdbcRepository extends PagingAndSortingRepository<TimeDepositIdTable, Integer> {
}
