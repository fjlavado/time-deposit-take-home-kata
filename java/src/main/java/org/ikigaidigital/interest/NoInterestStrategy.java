package org.ikigaidigital.interest;

import org.ikigaidigital.TimeDeposit;

public final class NoInterestStrategy implements InterestStrategy {
    @Override
    public double calculateInterest(TimeDeposit deposit) {
        return 0.0;
    }
}
