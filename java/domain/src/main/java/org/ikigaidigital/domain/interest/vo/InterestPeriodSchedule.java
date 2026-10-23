package org.ikigaidigital.domain.interest.vo;

import java.util.stream.IntStream;

/** Plan-specific credit dates in fixed 30-day months; interest amounts belong to the calculator. */
public record InterestPeriodSchedule(PlanType value) implements ValueObject<PlanType> {
    public IntStream pendingDays(int days, int lastProcessedDays) {
        boolean premium = PlanType.PREMIUM.equals(value);
        boolean student = PlanType.STUDENT.equals(value);
        if (!premium && !student && !PlanType.BASIC.equals(value)) {
            return IntStream.empty();
        }
        int first = premium ? 46 : 31;
        int end = student ? Math.min(days, 365) : days;
        // Long arithmetic avoids overflow near Integer.MAX_VALUE.
        long next = lastProcessedDays < first ? first
                : first + (((long) lastProcessedDays - first) / 30 + 1) * 30;
        int count = next > end ? 0 : (int) ((end - next) / 30 + 1);
        return IntStream.range(0, count).map(index -> (int) (next + (long) index * 30));
    }
}
