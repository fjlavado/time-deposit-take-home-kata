package org.ikigaidigital.interest;

import org.ikigaidigital.domain.interest.TimeDeposit;
import org.ikigaidigital.domain.interest.vo.PlanType;
import org.ikigaidigital.services.interest.InterestStrategy;
import org.ikigaidigital.services.interest.InterestStrategyResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.ikigaidigital.services.interest.BasicInterestStrategy.BASIC_INTEREST_STRATEGY;
import static org.ikigaidigital.services.interest.NoInterestStrategy.NO_INTEREST_STRATEGY;
import static org.ikigaidigital.services.interest.PremiumInterestStrategy.PREMIUM_INTEREST_STRATEGY;
import static org.ikigaidigital.services.interest.StudentInterestStrategy.STUDENT_INTEREST_STRATEGY;

class InterestStrategyResolverTest {
    @ParameterizedTest
    @MethodSource("builtInStrategies")
    void resolvesEligibleBuiltInPlans(String plan, int days, InterestStrategy expected) {
        InterestStrategyResolver resolver = new InterestStrategyResolver();

        assertThat(resolver.resolve(deposit(plan, days))).isSameAs(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "basic, 30", "student, 30", "student, 366", "premium, 45"
    })
    void resolvesMatchingButIneligiblePlansToNoInterest(String plan, int days) {
        InterestStrategyResolver resolver = new InterestStrategyResolver();

        assertThat(resolver.resolve(deposit(plan, days))).isSameAs(NO_INTEREST_STRATEGY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown", "", "Basic", "STUDENT", "Premium", " basic", "premium "})
    void resolvesUnregisteredPlanNamesToNoInterest(String plan) {
        InterestStrategyResolver resolver = new InterestStrategyResolver();

        assertThat(resolver.resolve(deposit(plan, 46))).isSameAs(NO_INTEREST_STRATEGY);
    }

    @Test
    void resolvesAliasesAndOverridesToTheSuppliedPermittedStrategies() {
        InterestStrategy custom = PREMIUM_INTEREST_STRATEGY;
        InterestStrategy replacement = NO_INTEREST_STRATEGY;
        InterestStrategyResolver resolver = new InterestStrategyResolver(Map.of(
                new PlanType("custom"), custom, PlanType.BASIC, replacement
        ));

        assertThat(resolver.resolve(deposit("custom", 46))).isSameAs(custom);
        assertThat(resolver.resolve(deposit("basic", 31))).isSameAs(replacement);
        assertThat(resolver.resolve(deposit("student", 46))).isSameAs(NO_INTEREST_STRATEGY);
    }

    @ParameterizedTest
    @CsvSource({"45, false", "46, true"})
    void evaluatesTheRegisteredStrategyEligibilityWithoutChangingTheDeposit(int days, boolean eligible) {
        PlanType customPlan = new PlanType("custom");
        TimeDeposit deposit = new TimeDeposit(1, customPlan, 1000.0, days);
        InterestStrategy custom = PREMIUM_INTEREST_STRATEGY;
        InterestStrategyResolver resolver = new InterestStrategyResolver(Map.of(customPlan, custom));

        InterestStrategy resolved = resolver.resolve(deposit);

        assertThat(resolved).isSameAs(eligible ? custom : NO_INTEREST_STRATEGY);
        assertThat(deposit.getBalance()).isEqualTo(1000.0);
        assertThat(deposit.getPlanType()).isEqualTo(customPlan);
        assertThat(deposit.getDays()).isEqualTo(days);
    }

    @Test
    void resolvesBuiltInPlansToNoInterestWhenTheRegistryIsEmpty() {
        InterestStrategyResolver resolver = new InterestStrategyResolver(Map.of());

        assertThat(resolver.resolve(deposit("basic", 46))).isSameAs(NO_INTEREST_STRATEGY);
        assertThat(resolver.resolve(deposit("student", 46))).isSameAs(NO_INTEREST_STRATEGY);
        assertThat(resolver.resolve(deposit("premium", 46))).isSameAs(NO_INTEREST_STRATEGY);
    }

    @Test
    void snapshotsTheRegistrySoCallerMutationsDoNotAffectResolution() {
        InterestStrategy original = BASIC_INTEREST_STRATEGY;
        PlanType customPlan = new PlanType("custom");
        Map<PlanType, InterestStrategy> registry = new HashMap<>();
        registry.put(customPlan, original);
        InterestStrategyResolver resolver = new InterestStrategyResolver(registry);
        registry.put(customPlan, PREMIUM_INTEREST_STRATEGY);
        registry.put(PlanType.BASIC, PREMIUM_INTEREST_STRATEGY);

        assertThat(resolver.resolve(deposit("custom", 46))).isSameAs(original);
        assertThat(resolver.resolve(deposit("basic", 46))).isSameAs(NO_INTEREST_STRATEGY);
    }

    private static TimeDeposit deposit(String plan, int days) {
        return new TimeDeposit(1, new PlanType(plan), 1000.0, days);
    }

    private static Stream<Arguments> builtInStrategies() {
        return Stream.of(
                Arguments.of("basic", 31, BASIC_INTEREST_STRATEGY),
                Arguments.of("student", 31, STUDENT_INTEREST_STRATEGY),
                Arguments.of("premium", 46, PREMIUM_INTEREST_STRATEGY)
        );
    }
}
