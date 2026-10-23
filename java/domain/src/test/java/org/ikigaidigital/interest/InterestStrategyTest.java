package org.ikigaidigital.interest;

import org.ikigaidigital.domain.interest.TimeDeposit;
import org.ikigaidigital.domain.interest.vo.PlanType;
import org.ikigaidigital.services.interest.InterestStrategy;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.ikigaidigital.services.interest.BasicInterestStrategy.BASIC_INTEREST_STRATEGY;
import static org.ikigaidigital.services.interest.NoInterestStrategy.NO_INTEREST_STRATEGY;
import static org.ikigaidigital.services.interest.PremiumInterestStrategy.PREMIUM_INTEREST_STRATEGY;
import static org.ikigaidigital.services.interest.StudentInterestStrategy.STUDENT_INTEREST_STRATEGY;

class InterestStrategyTest {
    @ParameterizedTest
    @CsvSource({"0, 0", "6, 0.01", "18, 0.02", "17.88, 0.01", "18.12, 0.02",
            "-6, -0.01", "-18, -0.02"})
    void roundsInterestHalfUpUsingTheDoubleDecimalRepresentation(double balance, double expected) {
        TimeDeposit deposit = new TimeDeposit(1, PlanType.BASIC, balance, 46);

        assertThat(BASIC_INTEREST_STRATEGY.calculateInterestRounded(deposit)).isEqualTo(expected);
        assertThat(deposit.getBalance()).isEqualTo(balance);
    }

    @ParameterizedTest
    @MethodSource("strategyPlanTypes")
    void declaresItsPlanType(InterestStrategy strategy, PlanType expected) {
        assertThat(strategy.planType()).isEqualTo(expected);
    }

    private static Stream<Arguments> strategyPlanTypes() {
        return Stream.of(
                Arguments.of(BASIC_INTEREST_STRATEGY, PlanType.BASIC),
                Arguments.of(STUDENT_INTEREST_STRATEGY, PlanType.STUDENT),
                Arguments.of(PREMIUM_INTEREST_STRATEGY, PlanType.PREMIUM),
                Arguments.of(NO_INTEREST_STRATEGY, PlanType.UNKNOWN)
        );
    }

    @ParameterizedTest(name = "{0} day {2}: applicable={3}")
    @MethodSource("eligibilityCases")
    void appliesTheStrategyOrFallsBackToNoInterest(String plan, InterestStrategy strategy, int days, boolean eligible) {
        TimeDeposit deposit = new TimeDeposit(7, new PlanType(plan), 1000.0, days);

        assertThat(strategy.apply(deposit)).isSameAs(eligible ? strategy : NO_INTEREST_STRATEGY);
        assertThat(deposit.getBalance()).isEqualTo(1000.0);
        assertThat(deposit.getDays()).isEqualTo(days);
    }

    @ParameterizedTest(name = "{0} day {2}: {3}")
    @MethodSource("interestCases")
    void calculatesUnroundedInterestWithoutMutatingTheDeposit(
            String plan, InterestStrategy strategy, int days, double expected) {
        TimeDeposit deposit = new TimeDeposit(7, new PlanType(plan), 1000.0, days);

        double interest = strategy.calculateInterest(deposit);

        assertThat(interest).isEqualTo(expected);
        assertThat(deposit.getBalance()).isEqualTo(1000.0);
        assertThat(deposit.getId()).isEqualTo(7);
        assertThat(deposit.getPlanType()).isEqualTo(new PlanType(plan));
        assertThat(deposit.getDays()).isEqualTo(days);
    }

    private static Stream<Arguments> interestCases() {
        return Stream.of(
                Arguments.of("basic", BASIC_INTEREST_STRATEGY, 31, 0.8333333333333334),
                Arguments.of("student", STUDENT_INTEREST_STRATEGY, 31, 2.5),
                Arguments.of("premium", PREMIUM_INTEREST_STRATEGY, 46, 4.166666666666667),
                Arguments.of("unknown", NO_INTEREST_STRATEGY, 0, 0.0)
        );
    }

    private static Stream<Arguments> eligibilityCases() {
        return Stream.of(
                Arguments.of("basic", BASIC_INTEREST_STRATEGY, 30, false),
                Arguments.of("basic", BASIC_INTEREST_STRATEGY, 31, true),
                Arguments.of("student", STUDENT_INTEREST_STRATEGY, 30, false),
                Arguments.of("student", STUDENT_INTEREST_STRATEGY, 31, true),
                Arguments.of("student", STUDENT_INTEREST_STRATEGY, 365, true),
                Arguments.of("student", STUDENT_INTEREST_STRATEGY, 366, false),
                Arguments.of("premium", PREMIUM_INTEREST_STRATEGY, 45, false),
                Arguments.of("premium", PREMIUM_INTEREST_STRATEGY, 46, true),
                Arguments.of("unknown", NO_INTEREST_STRATEGY, 0, true)
        );
    }
}
