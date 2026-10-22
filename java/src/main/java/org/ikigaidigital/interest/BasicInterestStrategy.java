package org.ikigaidigital.interest;

import org.ikigaidigital.TimeDeposit;

public final class BasicInterestStrategy implements InterestStrategy {
    @Override
    public boolean isApplicable(TimeDeposit deposit) {
        return deposit.getDays() > 30;
    }

    @Override
    public double calculateInterest(TimeDeposit deposit) {
        // Preserve the existing interpretation: annual rate divided into monthly interest.
        return deposit.getBalance() * 0.01 / 12;
    }
}
