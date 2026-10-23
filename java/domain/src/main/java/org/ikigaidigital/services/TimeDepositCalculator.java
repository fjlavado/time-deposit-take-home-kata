package org.ikigaidigital.services;

import org.ikigaidigital.domain.interest.TimeDeposit;
import org.ikigaidigital.domain.interest.vo.PlanType;
import org.ikigaidigital.services.interest.InterestStrategy;
import org.ikigaidigital.services.interest.InterestStrategyResolver;

import java.util.List;
import java.util.Map;

public class TimeDepositCalculator {
    private final InterestStrategyResolver strategyResolver;

    public TimeDepositCalculator() {
        this.strategyResolver = new InterestStrategyResolver();
    }

    /** Uses the supplied map as the complete registry of supported plans. */
    public TimeDepositCalculator(Map<PlanType, InterestStrategy> strategies) {
        this.strategyResolver = new InterestStrategyResolver(strategies);
    }

    public void updateBalance(List<TimeDeposit> xs) {
        for (final TimeDeposit deposit : xs) {
            final double interest = strategyResolver.resolve(deposit)
                    .calculateInterestRounded(deposit);
            deposit.updateBalance(interest);
        }
    }
}
