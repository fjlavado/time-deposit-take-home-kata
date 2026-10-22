package org.ikigaidigital.interest;

import org.ikigaidigital.TimeDeposit;

import java.util.Map;

public final class InterestStrategyResolver {
    private final Map<String, InterestStrategy> strategies;
    private final InterestStrategy noInterestStrategy = new NoInterestStrategy();

    public InterestStrategyResolver() {
        this(Map.of(
                "basic", new BasicInterestStrategy(),
                "student", new StudentInterestStrategy(),
                "premium", new PremiumInterestStrategy()
        ));
    }

    /** Uses the supplied map as the complete registry of supported plans. */
    public InterestStrategyResolver(Map<String, InterestStrategy> strategies) {
        this.strategies = Map.copyOf(strategies);
    }

    public InterestStrategy resolve(TimeDeposit deposit) {
        InterestStrategy strategy = strategies.getOrDefault(deposit.getPlanType(), noInterestStrategy);
        return strategy.isApplicable(deposit) ? strategy : noInterestStrategy;
    }
}
