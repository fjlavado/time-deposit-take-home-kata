package org.ikigaidigital;

import org.ikigaidigital.domain.interest.TimeDeposit;
import org.ikigaidigital.domain.interest.vo.PlanType;
import org.ikigaidigital.services.TimeDepositCalculator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TimeDepositCalculatorTest {
    private final TimeDepositCalculator calculator = new TimeDepositCalculator();

    @ParameterizedTest(name = "{0} at day {1} yields {2}")
    @CsvSource({
            "basic, 0, 1200", "basic, 29, 1200", "basic, 30, 1200",
            "basic, 31, 1201", "basic, 365, 1201", "basic, 366, 1201",
            "basic, 1000, 1201",
            "student, 0, 1200", "student, 29, 1200", "student, 30, 1200",
            "student, 31, 1203", "student, 364, 1203", "student, 365, 1203",
            "student, 366, 1200", "student, 1000, 1200",
            "premium, 0, 1200", "premium, 29, 1200", "premium, 30, 1200",
            "premium, 31, 1200", "premium, 44, 1200", "premium, 45, 1200",
            "premium, 46, 1205", "premium, 365, 1205", "premium, 366, 1205",
            "premium, 1000, 1205"
    })
    void appliesExistingRatesOnlyOnEligibleDays(String plan, int days, double expected) {
        TimeDeposit deposit = new TimeDeposit(1, new PlanType(plan), 1200.0, days);

        calculator.updateBalance(List.of(deposit));

        assertThat(deposit.getBalance()).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} balance {1} yields {2}")
    @CsvSource({
            "basic, 1234567, 1235595.81",
            "basic, 1000, 1000.83", "student, 1000, 1002.50", "premium, 1000, 1004.17",
            "basic, 0, 0", "student, 0, 0", "premium, 0, 0",
            "basic, 1, 1", "student, 1, 1", "premium, 1, 1",
            "basic, 6, 6.01", "basic, 18, 18.02",
            "student, 2, 2.01", "student, 6, 6.02",
            "premium, 1.2, 1.21", "premium, 3.6, 3.62",
            "basic, 1234.567, 1235.597",
            "student, 1234.567, 1237.657",
            "premium, 1234.567, 1239.707",
            "basic, 1200000000, 1201000000",
            "student, 1200000000, 1203000000",
            "premium, 1200000000, 1205000000"
    })
    void roundsInterestHalfUpAndDoesNotRoundFinalBalance(String plan, double balance, double expected) {
        TimeDeposit deposit = new TimeDeposit(1, new PlanType(plan), balance, 46);

        calculator.updateBalance(List.of(deposit));

        // Exact equality deliberately detects changes to binary arithmetic and rounding order.
        assertThat(deposit.getBalance()).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown", "", "Basic", "STUDENT", "Premium", " basic", "premium "})
    void unrecognizedPlanNamesEarnNoInterest(String plan) {
        TimeDeposit deposit = new TimeDeposit(1, new PlanType(plan), 1234.567, 100);

        calculator.updateBalance(List.of(deposit));

        assertThat(deposit.getBalance()).isEqualTo(1234.567);
    }

    @Test
    void acceptsAnEmptyList() {
        calculator.updateBalance(List.of());
    }

    @Test
    void updatesMixedDepositsInPlaceWithoutChangingTheirMetadataOrOrder() {
        TimeDeposit basic = new TimeDeposit(11, PlanType.BASIC, 1200.0, 31);
        TimeDeposit student = new TimeDeposit(12, PlanType.STUDENT, 1200.0, 365);
        TimeDeposit expiredStudent = new TimeDeposit(13, PlanType.STUDENT, 1200.0, 366);
        TimeDeposit premium = new TimeDeposit(14, PlanType.PREMIUM, 1200.0, 46);
        TimeDeposit waitingPremium = new TimeDeposit(15, PlanType.PREMIUM, 1200.0, 45);
        TimeDeposit unknown = new TimeDeposit(16, new PlanType("unknown"), 1200.0, 100);
        List<TimeDeposit> deposits = List.of(basic, student, expiredStudent, premium, waitingPremium, unknown);

        calculator.updateBalance(deposits);

        assertThat(deposits).containsExactly(basic, student, expiredStudent, premium, waitingPremium, unknown);
        assertThat(deposits).extracting(TimeDeposit::getBalance)
                .containsExactly(1201.0, 1203.0, 1200.0, 1205.0, 1200.0, 1200.0);
        assertThat(deposits).extracting(TimeDeposit::getId).containsExactly(11, 12, 13, 14, 15, 16);
        assertThat(deposits).extracting(TimeDeposit::getPlanType)
                .containsExactly(PlanType.BASIC, PlanType.STUDENT, PlanType.STUDENT,
                        PlanType.PREMIUM, PlanType.PREMIUM, new PlanType("unknown"));
        assertThat(deposits).extracting(TimeDeposit::getDays).containsExactly(31, 365, 366, 46, 45, 100);
    }

    @ParameterizedTest
    @CsvSource({"basic, 1001.6600000000001", "student, 1005.01", "premium, 1008.3499999999999"})
    void repeatedUpdatesUseTheCurrentBalance(String plan, double expected) {
        TimeDeposit deposit = new TimeDeposit(1, new PlanType(plan), 1000.0, 46);

        calculator.updateBalance(List.of(deposit));
        calculator.updateBalance(List.of(deposit));

        assertThat(deposit.getBalance()).isEqualTo(expected);
        assertThat(deposit.getDays()).isEqualTo(46);
    }

    @Test
    void processesEachListEntryEvenWhenTheSameDepositAppearsTwice() {
        TimeDeposit deposit = new TimeDeposit(1, PlanType.BASIC, 1000.0, 31);

        calculator.updateBalance(List.of(deposit, deposit));

        assertThat(deposit.getBalance()).isEqualTo(1001.6600000000001);
    }
}
