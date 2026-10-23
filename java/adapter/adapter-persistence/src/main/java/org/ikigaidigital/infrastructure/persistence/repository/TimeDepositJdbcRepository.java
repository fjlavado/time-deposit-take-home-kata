package org.ikigaidigital.infrastructure.persistence.repository;

import org.ikigaidigital.infrastructure.persistence.jdbc.TimeDepositTable;
import org.springframework.data.repository.CrudRepository;

public interface TimeDepositJdbcRepository extends CrudRepository<TimeDepositTable, Integer> {
}
