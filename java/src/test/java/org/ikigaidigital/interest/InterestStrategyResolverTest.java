package org.ikigaidigital.interest;

import org.ikigaidigital.TimeDeposit;
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

class InterestStrategyResolverTest {
    @ParameterizedTest
    @MethodSource("builtInStrategies")
    void resolvesEligibleBuiltInPlans(String plan, int days, Class<? extends InterestStrategy> expectedType) {
        InterestStrategyResolver resolver = new InterestStrategyResolver();

        assertThat(resolver.resolve(deposit(plan, days))).isInstanceOf(expectedType);
    }

    @ParameterizedTest
    @CsvSource({
            "basic, 0", "basic, 29", "basic, 30",
            "student, 0", "student, 29", "student, 30", "student, 366", "student, 1000",
            "premium, 0", "premium, 29", "premium, 30", "premium, 31", "premium, 44", "premium, 45"
    })
    void resolvesMatchingButIneligiblePlansToNoInterest(String plan, int days) {
        InterestStrategyResolver resolver = new InterestStrategyResolver();

        assertThat(resolver.resolve(deposit(plan, days))).isInstanceOf(NoInterestStrategy.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown", "", "Basic", "STUDENT", "Premium", " basic", "premium "})
    void resolvesUnregisteredPlanNamesToNoInterest(String plan) {
        InterestStrategyResolver resolver = new InterestStrategyResolver();

        assertThat(resolver.resolve(deposit(plan, 46))).isInstanceOf(NoInterestStrategy.class);
    }

    @Test
    void resolvesCustomRegistrationsAndOverridesToTheSuppliedStrategies() {
        InterestStrategy custom = deposit -> 10.0;
        InterestStrategy replacement = deposit -> 42.0;
        InterestStrategyResolver resolver = new InterestStrategyResolver(Map.of(
                "custom", custom, "basic", replacement
        ));

        assertThat(resolver.resolve(deposit("custom", 46))).isSameAs(custom);
        assertThat(resolver.resolve(deposit("basic", 0))).isSameAs(replacement);
        assertThat(resolver.resolve(deposit("student", 46))).isInstanceOf(NoInterestStrategy.class);
    }

    @ParameterizedTest
    @CsvSource({"9, 1000, false", "10, 999, false", "10, 1000, true"})
    void evaluatesCustomEligibilityUsingTheWholeDeposit(int days, double balance, boolean eligible) {
        TimeDeposit deposit = new TimeDeposit(1, "custom", balance, days);
        InterestStrategy custom = new InterestStrategy() {
            @Override
            public boolean isApplicable(TimeDeposit candidate) {
                assertThat(candidate).isSameAs(deposit);
                return candidate.getDays() >= 10 && candidate.getBalance() >= 1000;
            }

            @Override
            public double calculateInterest(TimeDeposit candidate) {
                throw new AssertionError("Resolution must not calculate interest");
            }
        };
        InterestStrategyResolver resolver = new InterestStrategyResolver(Map.of("custom", custom));

        InterestStrategy resolved = resolver.resolve(deposit);

        if (eligible) {
            assertThat(resolved).isSameAs(custom);
        } else {
            assertThat(resolved).isInstanceOf(NoInterestStrategy.class);
        }
    }

    @Test
    void resolvesBuiltInPlansToNoInterestWhenTheRegistryIsEmpty() {
        InterestStrategyResolver resolver = new InterestStrategyResolver(Map.of());

        assertThat(resolver.resolve(deposit("basic", 46))).isInstanceOf(NoInterestStrategy.class);
        assertThat(resolver.resolve(deposit("student", 46))).isInstanceOf(NoInterestStrategy.class);
        assertThat(resolver.resolve(deposit("premium", 46))).isInstanceOf(NoInterestStrategy.class);
    }

    @Test
    void snapshotsTheRegistrySoCallerMutationsDoNotAffectResolution() {
        InterestStrategy original = deposit -> 1.0;
        Map<String, InterestStrategy> registry = new HashMap<>();
        registry.put("custom", original);
        InterestStrategyResolver resolver = new InterestStrategyResolver(registry);
        registry.put("custom", deposit -> 99.0);
        registry.put("basic", deposit -> 99.0);

        assertThat(resolver.resolve(deposit("custom", 46))).isSameAs(original);
        assertThat(resolver.resolve(deposit("basic", 46))).isInstanceOf(NoInterestStrategy.class);
    }

    private static TimeDeposit deposit(String plan, int days) {
        return new TimeDeposit(1, plan, 1000.0, days);
    }

    private static Stream<Arguments> builtInStrategies() {
        return Stream.of(
                Arguments.of("basic", 31, BasicInterestStrategy.class),
                Arguments.of("basic", 365, BasicInterestStrategy.class),
                Arguments.of("basic", 366, BasicInterestStrategy.class),
                Arguments.of("student", 31, StudentInterestStrategy.class),
                Arguments.of("student", 364, StudentInterestStrategy.class),
                Arguments.of("student", 365, StudentInterestStrategy.class),
                Arguments.of("premium", 46, PremiumInterestStrategy.class),
                Arguments.of("premium", 365, PremiumInterestStrategy.class),
                Arguments.of("premium", 366, PremiumInterestStrategy.class)
        );
    }
}
