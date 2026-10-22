package org.ikigaidigital.interest;

import org.ikigaidigital.TimeDeposit;

public final class StudentInterestStrategy implements InterestStrategy {
    @Override
    public boolean isApplicable(TimeDeposit deposit) {
        return deposit.getDays() > 30 && deposit.getDays() < 366;
    }

    @Override
    public double calculateInterest(TimeDeposit deposit) {
        return deposit.getBalance() * 0.03 / 12;
    }
}
