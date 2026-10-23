package org.ikigaidigital.services.interest;

import org.ikigaidigital.domain.interest.TimeDeposit;
import org.ikigaidigital.domain.interest.vo.PlanType;

public final class NoInterestStrategy implements InterestStrategy {

    public static final NoInterestStrategy NO_INTEREST_STRATEGY = new NoInterestStrategy();

    private NoInterestStrategy() {}

    @Override
    public PlanType planType() {
        return PlanType.UNKNOWN;
    }

    @Override
    public double calculateInterest(TimeDeposit deposit) {
        return 0.0;
    }
}
