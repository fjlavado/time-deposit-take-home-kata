package org.ikigaidigital.application.query.handler;

import org.ikigaidigital.application.query.RetrieveTimeDepositsQuery;
import org.ikigaidigital.TimeDeposit;
import org.ikigaidigital.domain.port.out.TimeDepositRepository;

import java.util.List;

public final class RetrieveTimeDepositsQueryHandler
        implements QueryHandler<RetrieveTimeDepositsQuery, List<TimeDeposit>> {
    private final TimeDepositRepository repository;

    public RetrieveTimeDepositsQueryHandler(TimeDepositRepository repository) {
        this.repository = repository;
    }

    @Override
    public Class<RetrieveTimeDepositsQuery> queryType() {
        return RetrieveTimeDepositsQuery.class;
    }

    @Override
    public List<TimeDeposit> handle(RetrieveTimeDepositsQuery query) {
        return repository.findAll();
    }
}
