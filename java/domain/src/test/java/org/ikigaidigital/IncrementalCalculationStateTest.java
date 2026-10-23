package org.ikigaidigital;

import org.ikigaidigital.domain.interest.TimeDeposit;
import org.ikigaidigital.domain.interest.vo.PlanType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IncrementalCalculationStateTest {
    @Test
    void schedulesOnlyUnprocessedMonthlyPeriodsAcrossEligibilityBoundaries() {
        assertThat(deposit(PlanType.BASIC, 30, 0).pendingInterestDays()).isEmpty();
        assertThat(deposit(PlanType.BASIC, 91, 31).pendingInterestDays()).containsExactly(61, 91);
        assertThat(deposit(PlanType.BASIC, 60, 32).pendingInterestDays()).isEmpty();
        assertThat(deposit(PlanType.PREMIUM, 45, 0).pendingInterestDays()).isEmpty();
        assertThat(deposit(PlanType.PREMIUM, 76, 0).pendingInterestDays()).containsExactly(46, 76);
        assertThat(deposit(PlanType.STUDENT, 400, 331).pendingInterestDays()).containsExactly(361);
        assertThat(deposit(PlanType.STUDENT, 400, 365).pendingInterestDays()).isEmpty();
        assertThat(deposit(PlanType.UNKNOWN, 400, 0).pendingInterestDays()).isEmpty();
    }

    @Test
    void acknowledgingDaysMakesProcessingIdempotentWithoutChangingMetadataOrVersion() {
        var deposit = deposit(PlanType.BASIC, 91, 0);
        deposit.markCalculationProcessed();
        deposit.markCalculationProcessed();

        assertThat(deposit.pendingInterestDays()).isEmpty();
        assertThat(deposit.getLastProcessedDays()).isEqualTo(91);
        assertThat(deposit.getDays()).isEqualTo(91);
        assertThat(deposit.getVersion()).isEqualTo(7);
        assertThat(deposit.getBalance()).isEqualTo(1000.0);
        var regressed = deposit(PlanType.BASIC, 60, 91);
        regressed.markCalculationProcessed();
        assertThat(regressed.getLastProcessedDays()).isEqualTo(91);
    }

    @Test
    void handlesMaximumDayCountWithoutOverflowAndRejectsInvalidState() {
        assertThat(deposit(PlanType.BASIC, Integer.MAX_VALUE, Integer.MAX_VALUE - 30)
                .pendingInterestDays()).containsExactly(2147483641);
        assertThatThrownBy(() -> deposit(PlanType.BASIC, 31, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private TimeDeposit deposit(PlanType plan, int days, int processed) {
        return new TimeDeposit(1, plan, 1000.0, days, List.of(), processed, 7);
    }
}
