package org.ikigaidigital.application.port.in;

public interface UpdateDepositBalancesUseCase {
    /** Apply the calculator once to every deposit and persist all groups atomically. */
    void updateBalances();
}
