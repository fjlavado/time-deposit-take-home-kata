package org.ikigaidigital.services.interest;

import org.ikigaidigital.domain.interest.TimeDeposit;
import org.ikigaidigital.domain.interest.vo.PlanType;

import static org.ikigaidigital.services.interest.NoInterestStrategy.NO_INTEREST_STRATEGY;

public final class BasicInterestStrategy implements InterestStrategy {

    public static final InterestStrategy BASIC_INTEREST_STRATEGY = new BasicInterestStrategy();

    private BasicInterestStrategy() {}

    @Override
    public PlanType planType() {
        return PlanType.BASIC;
    }

    @Override
    public InterestStrategy apply(TimeDeposit deposit) {
        return deposit.getDays() > 30 ? BASIC_INTEREST_STRATEGY : NO_INTEREST_STRATEGY;
    }

    @Override
    public double calculateInterest(TimeDeposit deposit) {
        // Preserve the existing interpretation: annual rate divided into monthly interest.
        return deposit.getBalance() * 0.01 / 12;
    }
}
