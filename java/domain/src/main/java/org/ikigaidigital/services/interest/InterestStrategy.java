package org.ikigaidigital.services.interest;

import org.ikigaidigital.domain.interest.TimeDeposit;
import org.ikigaidigital.domain.interest.vo.PlanType;

import java.math.BigDecimal;
import java.math.RoundingMode;

public sealed interface InterestStrategy permits NoInterestStrategy, PremiumInterestStrategy, StudentInterestStrategy, BasicInterestStrategy {

    PlanType planType();
    /** Returns the eligible strategy, defaulting to the no-interest singleton. */
    default InterestStrategy apply(TimeDeposit deposit) {
        return NoInterestStrategy.NO_INTEREST_STRATEGY;
    }

    /** Returns monthly interest rounded to cents without mutating the eligible deposit. */
    default double calculateInterestRounded(TimeDeposit deposit) {
        // Preserve binary-double conversion: valueOf changes established half-cent results.
        return new BigDecimal(this.calculateInterest(deposit)).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    /** Returns unrounded monthly interest for an eligible deposit without mutating it. */
    double calculateInterest(TimeDeposit deposit);
}
