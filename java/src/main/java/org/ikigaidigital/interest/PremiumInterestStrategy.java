package org.ikigaidigital.interest;

import org.ikigaidigital.TimeDeposit;

public final class PremiumInterestStrategy implements InterestStrategy {
    @Override
    public boolean isApplicable(TimeDeposit deposit) {
        return deposit.getDays() > 45;
    }

    @Override
    public double calculateInterest(TimeDeposit deposit) {
        return deposit.getBalance() * 0.05 / 12;
    }
}
