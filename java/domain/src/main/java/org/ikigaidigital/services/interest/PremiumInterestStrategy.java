package org.ikigaidigital.services.interest;

import org.ikigaidigital.domain.interest.TimeDeposit;
import org.ikigaidigital.domain.interest.vo.PlanType;

import static org.ikigaidigital.services.interest.NoInterestStrategy.NO_INTEREST_STRATEGY;

public final class PremiumInterestStrategy implements InterestStrategy {

    public static final InterestStrategy PREMIUM_INTEREST_STRATEGY = new PremiumInterestStrategy();

    private PremiumInterestStrategy() {}
    @Override
    public PlanType planType() {
        return PlanType.PREMIUM;
    }

    @Override
    public InterestStrategy apply(TimeDeposit deposit) {
        return deposit.getDays() > 45 ? PREMIUM_INTEREST_STRATEGY : NO_INTEREST_STRATEGY ;
    }

    @Override
    public double calculateInterest(TimeDeposit deposit) {
        return deposit.getBalance() * 0.05 / 12;
    }
}
