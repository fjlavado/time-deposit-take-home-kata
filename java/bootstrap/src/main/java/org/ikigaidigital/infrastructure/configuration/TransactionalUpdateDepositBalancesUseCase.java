package org.ikigaidigital.infrastructure.configuration;

import org.ikigaidigital.application.port.in.UpdateDepositBalancesUseCase;
import org.springframework.transaction.annotation.Transactional;

/** Transaction begins before opening the cursor and commits only after every batch succeeds. */
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
