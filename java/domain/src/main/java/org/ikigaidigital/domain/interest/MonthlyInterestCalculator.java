package org.ikigaidigital.domain.interest;

import org.ikigaidigital.TimeDepositCalculator;
import org.ikigaidigital.domain.model.TimeDeposit;
import org.ikigaidigital.interest.InterestStrategy;

import java.util.List;
import java.util.Map;

public final class MonthlyInterestCalculator {
    private final TimeDepositCalculator calculator;

    public MonthlyInterestCalculator(Map<String, InterestStrategy> strategies) {
        this(new TimeDepositCalculator(strategies));
    }

    private MonthlyInterestCalculator(TimeDepositCalculator calculator) {
        this.calculator = calculator;
    }

    public static MonthlyInterestCalculator standardPlans() {
        return new MonthlyInterestCalculator(new TimeDepositCalculator());
    }

    public TimeDeposit applyTo(TimeDeposit deposit) {
        // Adapt the immutable backend model to the compatible mutable API so both use the same rules.
        var calculation = new org.ikigaidigital.TimeDeposit(
                deposit.id(), deposit.planType().value(), deposit.balance(), deposit.days());
        calculator.updateBalance(List.of(calculation));
        return deposit.withBalance(calculation.getBalance());
    }
}
