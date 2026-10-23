package org.ikigaidigital.infrastructure.transaction;

import org.ikigaidigital.application.port.in.UpdateDepositBalancesUseCase;
import org.springframework.transaction.annotation.Transactional;

/** Transaction begins before loading aggregates and commits only after every save group succeeds. */
public class TransactionalUpdateDepositBalancesUseCase implements UpdateDepositBalancesUseCase {
    private final UpdateDepositBalancesUseCase delegate;

    public TransactionalUpdateDepositBalancesUseCase(UpdateDepositBalancesUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public void updateBalances() {
        delegate.updateBalances();
    }
}
