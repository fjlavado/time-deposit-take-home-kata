package org.ikigaidigital.services.interest;

import org.ikigaidigital.domain.interest.TimeDeposit;
import org.ikigaidigital.domain.interest.vo.PlanType;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class InterestStrategyResolver {
    private final Map<PlanType, InterestStrategy> strategies;
    public InterestStrategyResolver() {
        this(Stream.of( BasicInterestStrategy.BASIC_INTEREST_STRATEGY,
                  StudentInterestStrategy.STUDENT_INTEREST_STRATEGY,
                  PremiumInterestStrategy.PREMIUM_INTEREST_STRATEGY)
                .collect(Collectors.toMap(InterestStrategy::planType, Function.identity())
        ));
    }

    /** Uses the supplied map as the complete registry of supported plans. */
    public InterestStrategyResolver(Map<PlanType, InterestStrategy> strategies) {
        this.strategies = Map.copyOf(strategies);
    }

    public InterestStrategy resolve(TimeDeposit deposit) {
        return strategies.getOrDefault(deposit.getPlanType(), NoInterestStrategy.NO_INTEREST_STRATEGY)
                    .apply(deposit);
    }
}
