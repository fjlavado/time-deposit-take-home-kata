package org.ikigaidigital.application.service;

import org.ikigaidigital.application.port.in.UpdateDepositBalancesUseCase;
import org.ikigaidigital.application.port.out.TimeDepositRepositoryPort;
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
        try (var ids = repository.streamAllIds()) {
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
        var deposits = repository.findAllById(ids);
        // Each request applies the existing calculator once at the deposit's current age.
        calculator.updateBalance(deposits);
        if (!deposits.isEmpty()) {
            repository.saveAll(deposits);
        }
    }
}
