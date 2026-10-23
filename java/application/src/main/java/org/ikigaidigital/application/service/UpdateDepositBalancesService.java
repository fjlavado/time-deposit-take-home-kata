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
        try (var ids = repository.streamPending()) {
            var iterator = ids.iterator();
            var group = new ArrayList<Integer>(BATCH_SIZE);
            while (iterator.hasNext()) {
                group.add(iterator.next());
                if (group.size() == BATCH_SIZE) {
                    updateGroup(List.copyOf(group));
                    group.clear();
                }
            }
            if (!group.isEmpty()) {
                updateGroup(List.copyOf(group));
            }
        }
    }

    private void updateGroup(List<Integer> ids) {
        // Another request may have processed these IDs since the cursor was opened.
        var deposits = repository.findAllById(ids).stream()
                .filter(deposit -> deposit.getDays() > deposit.getLastProcessedDays()).toList();

        for (var deposit : deposits) {
            deposit.pendingInterestDays().forEach(day -> {
                // Present the historical period's age to the existing calculator,
                // carrying the newly compounded balance forward one period at a time.
                var period = new TimeDeposit(deposit.getId(), deposit.getPlanType(), deposit.getBalance(), day);
                calculator.updateBalance(List.of(period));
                deposit.setBalance(period.getBalance());
            });
            deposit.markCalculationProcessed();
        }
        if (!deposits.isEmpty()) {
            repository.saveAll(deposits);
        }
    }
}
