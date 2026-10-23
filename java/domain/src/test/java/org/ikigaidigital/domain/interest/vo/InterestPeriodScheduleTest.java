package org.ikigaidigital.domain.interest.vo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InterestPeriodScheduleTest {
    @Test
    void schedulesOnlyUnprocessedMonthlyPeriodsAcrossEligibilityBoundaries() {
        var basic = new InterestPeriodSchedule(PlanType.BASIC);
        assertThat(basic.pendingDays(30, 0)).isEmpty();
        assertThat(basic.pendingDays(31, 0)).containsExactly(31);
        assertThat(basic.pendingDays(91, 31)).containsExactly(61, 91);
        assertThat(basic.pendingDays(60, 32)).isEmpty();
        assertThat(basic.pendingDays(60, 91)).isEmpty();

        var premium = new InterestPeriodSchedule(PlanType.PREMIUM);
        assertThat(premium.pendingDays(45, 0)).isEmpty();
        assertThat(premium.pendingDays(76, 0)).containsExactly(46, 76);

        var student = new InterestPeriodSchedule(PlanType.STUDENT);
        assertThat(student.pendingDays(365, 331)).containsExactly(361);
        assertThat(student.pendingDays(400, 331)).containsExactly(361);
        assertThat(student.pendingDays(400, 365)).isEmpty();
        assertThat(student.pendingDays(400, 0))
                .containsExactly(31, 61, 91, 121, 151, 181, 211, 241, 271, 301, 331, 361);
        assertThat(new InterestPeriodSchedule(PlanType.UNKNOWN).pendingDays(400, 0)).isEmpty();
        assertThat(new InterestPeriodSchedule(new PlanType("FuturePlan")).pendingDays(400, 0)).isEmpty();
    }

    @Test
    void handlesMaximumDayCountWithoutOverflow() {
        var schedule = new InterestPeriodSchedule(PlanType.BASIC);
        assertThat(schedule.pendingDays(Integer.MAX_VALUE, Integer.MAX_VALUE - 30)).containsExactly(2147483641);
        assertThat(schedule.pendingDays(Integer.MAX_VALUE, Integer.MAX_VALUE)).isEmpty();
    }
}
