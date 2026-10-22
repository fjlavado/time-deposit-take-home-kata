package org.ikigaidigital.infrastructure.persistence.jdbc;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** ID-only view for paging without triggering per-aggregate child reads. */
@Table("timeDeposits")
public record TimeDepositIdTable(@Id Integer id) {
}
