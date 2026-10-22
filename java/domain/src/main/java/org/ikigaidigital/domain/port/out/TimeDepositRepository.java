package org.ikigaidigital.domain.port.out;

import org.ikigaidigital.TimeDeposit;
import org.ikigaidigital.domain.model.Page;
import org.ikigaidigital.domain.model.PageRequest;

import java.util.List;

public interface TimeDepositRepository {
    Page<TimeDeposit> search(PageRequest request);

    /** Serialize balance updates before searching pages within the caller's transaction. */
    void lockForBalanceUpdate();

    /** Persist balances only; age, plan, and withdrawals must remain unchanged. */
    void saveBalances(List<TimeDeposit> deposits);
}
