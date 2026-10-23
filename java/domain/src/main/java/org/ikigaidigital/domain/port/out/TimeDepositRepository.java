package org.ikigaidigital.domain.port.out;

import org.ikigaidigital.domain.interest.TimeDeposit;
import org.ikigaidigital.domain.model.Page;
import org.ikigaidigital.domain.model.PageRequestVO;

import java.util.List;

public interface TimeDepositRepository {
    Page<TimeDeposit> search(PageRequestVO request);

    /** Serialize balance updates before searching pages within the caller's transaction. */
    void lockForBalanceUpdate();

    /** Persist balances only; age, plan, and withdrawals must remain unchanged. */
    void saveBalances(List<TimeDeposit> deposits);
}
