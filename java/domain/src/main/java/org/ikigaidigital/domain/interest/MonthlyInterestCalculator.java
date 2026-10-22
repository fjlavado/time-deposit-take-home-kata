package org.ikigaidigital.domain.interest;

import org.ikigaidigital.domain.model.PlanType;
import org.ikigaidigital.domain.model.TimeDeposit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class MonthlyInterestCalculator {
    private final List<InterestPolicy> policies;

    public MonthlyInterestCalculator(List<InterestPolicy> policies) {
        this.policies = List.copyOf(policies);
    }

    public static MonthlyInterestCalculator standardPlans() {
        return new MonthlyInterestCalculator(List.of(
                new AnnualRateInterestPolicy(PlanType.BASIC, 0.01, days -> days > 30),
                new AnnualRateInterestPolicy(PlanType.STUDENT, 0.03, days -> days > 30 && days < 366),
                new AnnualRateInterestPolicy(PlanType.PREMIUM, 0.05, days -> days > 45)
        ));
    }

    public TimeDeposit applyTo(TimeDeposit deposit) {
        double interest = policies.stream()
                .filter(policy -> policy.supports(deposit.planType()))
                .findFirst()
                .map(policy -> policy.monthlyInterest(deposit))
                .orElse(0.0);
        // Compatibility requires the binary double -> BigDecimal constructor, not valueOf.
        // Round only interest; rounding the resulting balance would change established behavior.
        double roundedInterest = new BigDecimal(interest).setScale(2, RoundingMode.HALF_UP).doubleValue();
        return deposit.withBalance(deposit.balance() + roundedInterest);
    }
}
