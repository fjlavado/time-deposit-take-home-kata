package org.ikigaidigital.application.service;

import org.ikigaidigital.application.port.in.UpdateDepositBalancesUseCase;
import org.ikigaidigital.domain.interest.TimeDeposit;
import org.ikigaidigital.domain.port.out.TimeDepositRepositoryPort;
import org.ikigaidigital.services.TimeDepositCalculator;

import java.util.ArrayList;
import java.util.List;

/** Framework-free orchestration; bootstrap decorates this use case with a transaction. */
public final class UpdateDepositBalancesService implements UpdateDepositBalancesUseCase {
    public static final int BATCH_SIZE = 100;

    private final TimeDepositRepositoryPort repository;
    private final TimeDepositCalculator calculator;

    public UpdateDepositBalancesService(TimeDepositRepositoryPort repository, TimeDepositCalculator calculator) {
        this.repository = repository;
        this.calculator = calculator;
    }

    @Override
    public void updateBalances() {
        try (var deposits = repository.streamPending()) {
            var iterator = deposits.iterator();
            var batch = new ArrayList<TimeDeposit>(BATCH_SIZE);
            while (iterator.hasNext()) {
                var deposit = iterator.next();
                deposit.pendingInterestDays().forEach(day -> {
                    // Present the historical period's age to the existing calculator,
                    // carrying the newly compounded balance forward one period at a time.
                    var period = new TimeDeposit(deposit.getId(), deposit.getPlanType(), deposit.getBalance(), day);
                    calculator.updateBalance(List.of(period));
                    deposit.setBalance(period.getBalance());
                });
                deposit.markCalculationProcessed();
                batch.add(deposit);
                if (batch.size() == BATCH_SIZE) {
                    repository.saveBatch(List.copyOf(batch));
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                repository.saveBatch(List.copyOf(batch));
            }
        }
    }
}
