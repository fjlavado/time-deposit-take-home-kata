package org.ikigaidigital.domain.port.out;

import org.ikigaidigital.domain.model.TimeDeposit;

import java.util.List;

public interface TimeDepositRepository {
    List<TimeDeposit> findAll();

    /** Load deposits for a serialized balance update within the caller's transaction. */
    List<TimeDeposit> findAllForBalanceUpdate();

    /** Persist balances only; age, plan, and withdrawals must remain unchanged. */
    void saveBalances(List<TimeDeposit> deposits);
}
