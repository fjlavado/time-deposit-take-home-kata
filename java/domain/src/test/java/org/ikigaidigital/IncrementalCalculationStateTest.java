package org.ikigaidigital;

import org.ikigaidigital.domain.interest.TimeDeposit;
import org.ikigaidigital.domain.interest.vo.PlanType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IncrementalCalculationStateTest {
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
    void rejectsInvalidCalculationState() {
        assertThatThrownBy(() -> deposit(PlanType.BASIC, 31, -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TimeDeposit(1, PlanType.BASIC, 1000.0, 31, List.of(), 0, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private TimeDeposit deposit(PlanType plan, int days, int processed) {
        return new TimeDeposit(1, plan, 1000.0, days, List.of(), processed, 7);
    }
}
