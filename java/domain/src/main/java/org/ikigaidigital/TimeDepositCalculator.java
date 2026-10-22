package org.ikigaidigital;

import org.ikigaidigital.interest.InterestStrategy;
import org.ikigaidigital.interest.InterestStrategyResolver;

import java.util.List;
import java.util.Map;

public class TimeDepositCalculator {
    private final InterestStrategyResolver strategyResolver;

    public TimeDepositCalculator() {
        this.strategyResolver = new InterestStrategyResolver();
    }

    /** Uses the supplied map as the complete registry of supported plans. */
    public TimeDepositCalculator(Map<String, InterestStrategy> strategies) {
        this.strategyResolver = new InterestStrategyResolver(strategies);
    }

    public void updateBalance(List<TimeDeposit> xs) {
        for (TimeDeposit deposit : xs) {
            double interest = strategyResolver.resolve(deposit)
                    .calculateInterestRounded(deposit);
            deposit.updateBalance(interest);
        }
    }
}
