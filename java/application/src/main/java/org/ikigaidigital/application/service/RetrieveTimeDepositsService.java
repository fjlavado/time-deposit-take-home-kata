package org.ikigaidigital.application.service;

import org.ikigaidigital.application.port.in.RetrieveTimeDepositsUseCase;
import org.ikigaidigital.domain.model.TimeDeposit;
import org.ikigaidigital.domain.port.out.TimeDepositRepository;

import java.util.List;

public final class RetrieveTimeDepositsService implements RetrieveTimeDepositsUseCase {
    private final TimeDepositRepository repository;

    public RetrieveTimeDepositsService(TimeDepositRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<TimeDeposit> retrieveAll() {
        return repository.findAll();
    }
}
