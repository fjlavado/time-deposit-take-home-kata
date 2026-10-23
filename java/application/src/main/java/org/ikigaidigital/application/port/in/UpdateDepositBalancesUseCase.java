package org.ikigaidigital.application.port.in;

public interface UpdateDepositBalancesUseCase {
    /** Process all pending monthly periods atomically; repeated calls at the same age are no-ops. */
    void updateBalances();
}
