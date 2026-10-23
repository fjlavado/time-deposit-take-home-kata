package org.ikigaidigital.application.query.handler;

import org.ikigaidigital.application.query.RetrieveTimeDepositsQuery;
import org.ikigaidigital.domain.interest.TimeDeposit;
import org.ikigaidigital.domain.model.Page;
import org.ikigaidigital.domain.model.PageRequest;
import org.ikigaidigital.domain.port.out.TimeDepositRepository;

public final class RetrieveTimeDepositsQueryHandler
        implements QueryHandler<RetrieveTimeDepositsQuery, Page<TimeDeposit>> {
    private final TimeDepositRepository repository;

    public RetrieveTimeDepositsQueryHandler(TimeDepositRepository repository) {
        this.repository = repository;
    }

    @Override
    public Class<RetrieveTimeDepositsQuery> queryType() {
        return RetrieveTimeDepositsQuery.class;
    }

    @Override
    public Page<TimeDeposit> handle(RetrieveTimeDepositsQuery query) {
        return repository.search(new PageRequest(query.page(), query.size()));
    }
}
