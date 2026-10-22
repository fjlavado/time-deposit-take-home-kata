package org.ikigaidigital.infrastructure.configuration;

import org.ikigaidigital.application.port.in.UpdateTimeDepositBalancesUseCase;
import org.springframework.transaction.support.TransactionTemplate;

public final class TransactionalBalanceUpdate implements UpdateTimeDepositBalancesUseCase {
    private final UpdateTimeDepositBalancesUseCase delegate;
    private final TransactionTemplate transaction;

    public TransactionalBalanceUpdate(UpdateTimeDepositBalancesUseCase delegate, TransactionTemplate transaction) {
        this.delegate = delegate;
        this.transaction = transaction;
    }

    @Override
    public void updateBalances() {
        transaction.executeWithoutResult(status -> delegate.updateBalances());
    }
}
