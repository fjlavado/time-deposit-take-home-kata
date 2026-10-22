package org.ikigaidigital.interest;

import org.ikigaidigital.TimeDeposit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class InterestStrategyTest {
    @ParameterizedTest
    @CsvSource({"0, 0", "0.005, 0.01", "0.015, 0.01", "0.0149, 0.01", "0.0151, 0.02"})
    void roundsInterestUsingTheEstablishedBinaryConversion(double interest, double expected) {
        InterestStrategy strategy = deposit -> interest;
        TimeDeposit deposit = new TimeDeposit(1, "custom", 1234.567, 46);

        assertThat(strategy.calculateInterestRounded(deposit)).isEqualTo(expected);
        assertThat(deposit.getBalance()).isEqualTo(1234.567);
    }

    @ParameterizedTest(name = "{0} day {2}: applicable={3}")
    @MethodSource("eligibilityCases")
    void assertsEligibility(String plan, InterestStrategy strategy, int days, boolean expected) {
        TimeDeposit deposit = new TimeDeposit(7, plan, 1000.0, days);

        assertThat(strategy.isApplicable(deposit)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} day {2}: {3}")
    @MethodSource("interestCases")
    void calculatesUnroundedInterestWithoutMutatingTheDeposit(
            String plan, InterestStrategy strategy, int days, double expected) {
        TimeDeposit deposit = new TimeDeposit(7, plan, 1000.0, days);

        double interest = strategy.calculateInterest(deposit);

        assertThat(interest).isEqualTo(expected);
        assertThat(deposit.getBalance()).isEqualTo(1000.0);
        assertThat(deposit.getId()).isEqualTo(7);
        assertThat(deposit.getPlanType()).isEqualTo(plan);
        assertThat(deposit.getDays()).isEqualTo(days);
    }

    private static Stream<Arguments> interestCases() {
        return Stream.of(
                Arguments.of("basic", new BasicInterestStrategy(), 31, 0.8333333333333334),
                Arguments.of("basic", new BasicInterestStrategy(), 366, 0.8333333333333334),
                Arguments.of("student", new StudentInterestStrategy(), 31, 2.5),
                Arguments.of("student", new StudentInterestStrategy(), 365, 2.5),
                Arguments.of("premium", new PremiumInterestStrategy(), 46, 4.166666666666667),
                Arguments.of("premium", new PremiumInterestStrategy(), 366, 4.166666666666667),
                Arguments.of("unknown", new NoInterestStrategy(), 0, 0.0),
                Arguments.of("basic", new NoInterestStrategy(), 31, 0.0),
                Arguments.of("premium", new NoInterestStrategy(), 366, 0.0)
        );
    }

    private static Stream<Arguments> eligibilityCases() {
        return Stream.of(
                Arguments.of("basic", new BasicInterestStrategy(), 30, false),
                Arguments.of("basic", new BasicInterestStrategy(), 31, true),
                Arguments.of("basic", new BasicInterestStrategy(), 366, true),
                Arguments.of("student", new StudentInterestStrategy(), 30, false),
                Arguments.of("student", new StudentInterestStrategy(), 31, true),
                Arguments.of("student", new StudentInterestStrategy(), 365, true),
                Arguments.of("student", new StudentInterestStrategy(), 366, false),
                Arguments.of("premium", new PremiumInterestStrategy(), 45, false),
                Arguments.of("premium", new PremiumInterestStrategy(), 46, true)
        );
    }
}
