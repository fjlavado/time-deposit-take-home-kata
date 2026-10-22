package org.ikigaidigital.interest;

import org.ikigaidigital.TimeDeposit;

import java.math.BigDecimal;
import java.math.RoundingMode;

@FunctionalInterface
public interface InterestStrategy {
    /** Eligibility assertion checked by the resolver; unconstrained strategies apply by default. */
    default boolean isApplicable(TimeDeposit deposit) {
        return true;
    }

    /** Returns monthly interest rounded to cents without mutating the eligible deposit. */
    default double calculateInterestRounded(TimeDeposit deposit) {
        // Preserve binary-double conversion: valueOf changes established half-cent results.
        return new BigDecimal(this.calculateInterest(deposit)).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    /** Returns unrounded monthly interest for an eligible deposit without mutating it. */
    double calculateInterest(TimeDeposit deposit);
}
