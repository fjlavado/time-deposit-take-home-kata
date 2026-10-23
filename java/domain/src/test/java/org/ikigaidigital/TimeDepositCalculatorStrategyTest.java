package org.ikigaidigital;

import org.ikigaidigital.domain.interest.TimeDeposit;
import org.ikigaidigital.domain.interest.vo.PlanType;
import org.ikigaidigital.services.interest.BasicInterestStrategy;
import org.ikigaidigital.services.interest.NoInterestStrategy;
import org.ikigaidigital.services.interest.PremiumInterestStrategy;
import org.ikigaidigital.services.TimeDepositCalculator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TimeDepositCalculatorStrategyTest {
    @Test
    void registersAnExistingStrategyUnderACustomPlanWithItsEligibilityAndRounding() {
        PlanType loyalty = new PlanType("loyalty");
        TimeDepositCalculator calculator = new TimeDepositCalculator(Map.of(
                PlanType.BASIC, BasicInterestStrategy.BASIC_INTEREST_STRATEGY,
                loyalty, PremiumInterestStrategy.PREMIUM_INTEREST_STRATEGY
        ));
        TimeDeposit waiting = new TimeDeposit(1, loyalty, 1000.0, 45);
        TimeDeposit eligible = new TimeDeposit(2, new PlanType("loyalty"), 1000.0, 46);
        TimeDeposit basic = new TimeDeposit(3, PlanType.BASIC, 1000.0, 31);

        calculator.updateBalance(List.of(waiting, eligible, basic));

        assertThat(waiting.getBalance()).isEqualTo(1000.0);
        assertThat(eligible.getBalance()).isEqualTo(1004.17);
        assertThat(basic.getBalance()).isEqualTo(1000.83);
    }

    @Test
    void canReplaceAnExistingPlanStrategy() {
        TimeDepositCalculator calculator = new TimeDepositCalculator(Map.of(PlanType.BASIC, NoInterestStrategy.NO_INTEREST_STRATEGY));
        TimeDeposit deposit = new TimeDeposit(1, PlanType.BASIC, 1000.0, 31);

        calculator.updateBalance(List.of(deposit));

        assertThat(deposit.getBalance()).isEqualTo(1000.0);
    }

    @Test
    void suppliedRegistryDoesNotImplicitlyIncludeBuiltInPlans() {
        TimeDepositCalculator calculator = new TimeDepositCalculator(Map.of(
                new PlanType("custom"), BasicInterestStrategy.BASIC_INTEREST_STRATEGY));
        List<TimeDeposit> deposits = List.of(
                new TimeDeposit(1, PlanType.BASIC, 1200.0, 46),
                new TimeDeposit(2, PlanType.STUDENT, 1200.0, 46),
                new TimeDeposit(3, PlanType.PREMIUM, 1200.0, 46)
        );

        calculator.updateBalance(deposits);

        assertThat(deposits).extracting(TimeDeposit::getBalance).containsExactly(1200.0, 1200.0, 1200.0);
    }
}
