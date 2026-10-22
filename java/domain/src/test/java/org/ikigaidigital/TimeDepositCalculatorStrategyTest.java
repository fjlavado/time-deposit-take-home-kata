package org.ikigaidigital;

import org.ikigaidigital.interest.BasicInterestStrategy;
import org.ikigaidigital.interest.InterestStrategy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TimeDepositCalculatorStrategyTest {
    @Test
    void registersACustomPlanWithItsOwnEligibilityAndCentralizedRounding() {
        InterestStrategy loyalty = new InterestStrategy() {
            @Override
            public boolean isApplicable(TimeDeposit deposit) {
                return deposit.getDays() >= 10;
            }

            @Override
            public double calculateInterest(TimeDeposit deposit) {
                assertThat(deposit.getDays()).isGreaterThanOrEqualTo(10);
                return deposit.getBalance() * 0.10 / 12;
            }
        };
        TimeDepositCalculator calculator = new TimeDepositCalculator(Map.of(
                "basic", new BasicInterestStrategy(),
                "loyalty", loyalty
        ));
        TimeDeposit waiting = new TimeDeposit(1, "loyalty", 1000.0, 9);
        TimeDeposit eligible = new TimeDeposit(2, "loyalty", 1000.0, 10);
        TimeDeposit basic = new TimeDeposit(3, "basic", 1000.0, 31);

        calculator.updateBalance(List.of(waiting, eligible, basic));

        assertThat(waiting.getBalance()).isEqualTo(1000.0);
        assertThat(eligible.getBalance()).isEqualTo(1008.33);
        assertThat(basic.getBalance()).isEqualTo(1000.83);
    }

    @Test
    void canReplaceAnExistingPlanStrategy() {
        TimeDepositCalculator calculator = new TimeDepositCalculator(Map.of("basic", deposit -> 42.0));
        TimeDeposit deposit = new TimeDeposit(1, "basic", 1000.0, 0);

        calculator.updateBalance(List.of(deposit));

        assertThat(deposit.getBalance()).isEqualTo(1042.0);
    }

    @Test
    void suppliedRegistryDoesNotImplicitlyIncludeBuiltInPlans() {
        TimeDepositCalculator calculator = new TimeDepositCalculator(Map.of("custom", deposit -> 1.0));
        List<TimeDeposit> deposits = List.of(
                new TimeDeposit(1, "basic", 1200.0, 46),
                new TimeDeposit(2, "student", 1200.0, 46),
                new TimeDeposit(3, "premium", 1200.0, 46)
        );

        calculator.updateBalance(deposits);

        assertThat(deposits).extracting(TimeDeposit::getBalance).containsExactly(1200.0, 1200.0, 1200.0);
    }

    @Test
    void anEmptyRegistryLeavesBalancesUnchanged() {
        TimeDepositCalculator calculator = new TimeDepositCalculator(Map.of());
        TimeDeposit deposit = new TimeDeposit(1, "basic", 1234.567, 100);

        calculator.updateBalance(List.of(deposit));

        assertThat(deposit.getBalance()).isEqualTo(1234.567);
    }

    @Test
    void laterMutationsToTheSuppliedRegistryDoNotChangeRouting() {
        Map<String, InterestStrategy> registry = new HashMap<>();
        registry.put("custom", deposit -> 1.0);
        TimeDepositCalculator calculator = new TimeDepositCalculator(registry);
        registry.put("custom", deposit -> 99.0);
        registry.put("basic", deposit -> 99.0);
        TimeDeposit custom = new TimeDeposit(1, "custom", 1000.0, 46);
        TimeDeposit basic = new TimeDeposit(2, "basic", 1000.0, 46);

        calculator.updateBalance(List.of(custom, basic));

        assertThat(custom.getBalance()).isEqualTo(1001.0);
        assertThat(basic.getBalance()).isEqualTo(1000.0);
    }

    @Test
    void delegatesEachMatchingDepositExactlyOnceAndNeverDelegatesUnknownPlans() {
        List<TimeDeposit> received = new ArrayList<>();
        InterestStrategy strategy = deposit -> {
            received.add(deposit);
            return 0.0;
        };
        TimeDepositCalculator calculator = new TimeDepositCalculator(Map.of("custom", strategy));
        TimeDeposit first = new TimeDeposit(1, "custom", 1000.0, 10);
        TimeDeposit second = new TimeDeposit(2, "custom", 2000.0, 20);
        TimeDeposit unknown = new TimeDeposit(3, "unknown", 3000.0, 30);

        calculator.updateBalance(List.of(first, unknown, second));

        assertThat(received).containsExactly(first, second);
    }
}
