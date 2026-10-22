package org.ikigaidigital.domain.interest;

import org.ikigaidigital.domain.model.PlanType;
import org.ikigaidigital.domain.model.TimeDeposit;
import org.ikigaidigital.domain.model.Withdrawal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MonthlyInterestCalculatorTest {
    private final MonthlyInterestCalculator calculator = MonthlyInterestCalculator.standardPlans();

    @ParameterizedTest
    @CsvSource({
            "basic, 0, 1000", "basic, 29, 1000", "basic, 30, 1000", "basic, 31, 1000.83",
            "basic, 366, 1000.83", "student, 30, 1000", "student, 31, 1002.50",
            "student, 365, 1002.50", "student, 366, 1000", "student, 367, 1000",
            "premium, 30, 1000", "premium, 31, 1000", "premium, 45, 1000",
            "premium, 46, 1004.17", "premium, 366, 1004.17", "future, 100, 1000",
            "Basic, 100, 1000", "STUDENT, 100, 1000"
    })
    void creditsOnlyEligiblePlans(String plan, int days, double expected) {
        var deposit = new TimeDeposit(1, new PlanType(plan), 1000, days, List.of());

        assertThat(calculator.applyTo(deposit).balance()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"6, 6.01", "18, 18.01", "1234567, 1235595.81", "0, 0"})
    void preservesBinaryDoubleRounding(double balance, double expected) {
        var deposit = new TimeDeposit(1, PlanType.BASIC, balance, 45, List.of());

        assertThat(calculator.applyTo(deposit).balance()).isEqualTo(expected);
    }

    @Test
    void leavesOriginalDepositAgeAndWithdrawalsUnchangedWithoutRoundingFinalBalance() {
        var withdrawal = new Withdrawal(2, new BigDecimal("2.50"), LocalDate.of(2026, 1, 2));
        var withdrawals = new ArrayList<>(List.of(withdrawal));
        var original = new TimeDeposit(1, PlanType.BASIC, 12.345, 31, withdrawals);
        withdrawals.clear();

        var updated = calculator.applyTo(original);

        assertThat(original.balance()).isEqualTo(12.345);
        assertThat(updated.balance()).isEqualTo(12.345 + 0.01);
        assertThat(updated.id()).isEqualTo(original.id());
        assertThat(updated.planType()).isEqualTo(original.planType());
        assertThat(updated.days()).isEqualTo(31);
        assertThat(updated.withdrawals()).containsExactly(withdrawal);
    }

    @Test
    void compoundsOnRepeatedInvocations() {
        var deposit = new TimeDeposit(1, PlanType.STUDENT, 1000, 31, List.of());

        assertThat(calculator.applyTo(calculator.applyTo(deposit)).balance()).isEqualTo(1005.01);
        var basic = new TimeDeposit(2, PlanType.BASIC, 1000, 31, List.of());
        assertThat(calculator.applyTo(calculator.applyTo(basic)).balance()).isEqualTo(1001.6600000000001);
    }

    @Test
    void acceptsFuturePoliciesWithoutChangingTheCalculator() {
        var future = new PlanType("future");
        var custom = new MonthlyInterestCalculator(List.of(
                new AnnualRateInterestPolicy(future, 0.12, days -> days > 60)));

        assertThat(custom.applyTo(new TimeDeposit(1, future, 1000, 61, List.of())).balance())
                .isEqualTo(1010);
    }
}
