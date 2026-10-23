package org.ikigaidigital.infrastructure.persistence;

import org.ikigaidigital.domain.interest.TimeDeposit;
import org.ikigaidigital.domain.port.out.TimeDepositRepository;
import org.ikigaidigital.domain.model.Page;
import org.ikigaidigital.domain.model.PageRequestVO;
import org.ikigaidigital.infrastructure.persistence.jdbc.TimeDepositIdTable;
import org.ikigaidigital.infrastructure.persistence.jdbc.TimeDepositTable;
import org.ikigaidigital.infrastructure.persistence.mapper.TimeDepositPersistenceMapper;
import org.ikigaidigital.infrastructure.persistence.repository.TimeDepositJdbcRepository;
import org.ikigaidigital.infrastructure.persistence.repository.TimeDepositPageJdbcRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.List;
import java.util.Comparator;
import java.util.stream.StreamSupport;

public final class JdbcTimeDepositRepository implements TimeDepositRepository {
    private final TimeDepositJdbcRepository deposits;
    private final TimeDepositPageJdbcRepository pages;
    private final TimeDepositPersistenceMapper mapper;

    public JdbcTimeDepositRepository(TimeDepositJdbcRepository deposits, TimeDepositPageJdbcRepository pages,
                                     TimeDepositPersistenceMapper mapper) {
        this.deposits = deposits;
        this.pages = pages;
        this.mapper = mapper;
    }

    @Override
    public Page<TimeDeposit> search(PageRequestVO request) {
        // Use inherited paging and database ordering, never an unbounded in-memory slice.
        var result = pages.findAll(PageRequest.of(
                request.page(), request.size(), Sort.by("id")));
        // JDBC's paged aggregate read falls back to child queries. Page IDs, then use
        // inherited findAllById for single-query loading of the bounded aggregate set.
        var ids = result.getContent().stream().map(TimeDepositIdTable::id).toList();
        var content = ids.isEmpty() ? List.<TimeDeposit>of()
                : StreamSupport.stream(deposits.findAllById(ids).spliterator(), false)
                        .sorted(Comparator.comparing(TimeDepositTable::id)).map(mapper::toDomain).toList();
        return new Page<>(content,
                result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Override
    public void lockForBalanceUpdate() {
        // The outer use-case transaction holds locks until all balances are saved.
        // Stable lock order serializes concurrent updates without lost interest credits.
        deposits.lockAllForBalanceUpdate();
    }

    @Override
    public void saveBalances(List<TimeDeposit> deposits) {
        deposits.forEach(deposit -> this.deposits.updateBalance(deposit.getId(),
                BigDecimal.valueOf(deposit.getBalance())));
    }
}
