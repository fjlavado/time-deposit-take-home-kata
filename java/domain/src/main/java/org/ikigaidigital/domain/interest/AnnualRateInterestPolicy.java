package org.ikigaidigital.domain.interest;

import org.ikigaidigital.domain.model.PlanType;
import org.ikigaidigital.domain.model.TimeDeposit;

import java.util.function.IntPredicate;

public record AnnualRateInterestPolicy(PlanType planType, double annualRate,
                                       IntPredicate eligibleDays) implements InterestPolicy {
    @Override
    public boolean supports(PlanType candidate) {
        return planType.equals(candidate);
    }

    @Override
    public double monthlyInterest(TimeDeposit deposit) {
        // The starter treats the stated percentages as annual rates, credited monthly.
        return eligibleDays.test(deposit.days()) ? deposit.balance() * annualRate / 12 : 0;
    }
}
