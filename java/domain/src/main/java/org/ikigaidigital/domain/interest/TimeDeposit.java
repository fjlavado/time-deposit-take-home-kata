package org.ikigaidigital.domain.interest;

import org.ikigaidigital.domain.interest.vo.Balance;
import org.ikigaidigital.domain.interest.vo.PlanType;
import org.ikigaidigital.domain.model.Withdrawal;

import java.util.List;
import java.util.stream.IntStream;

public class TimeDeposit {
    private final int id;
    private final PlanType planType;
    private Balance balance;
    private final int days;
    private final List<Withdrawal> withdrawals;
    private int lastProcessedDays;
    private final long version;

    public TimeDeposit(int id, PlanType planType, Double balance, int days) {
        this(id, planType, balance, days, List.of());
    }

    public TimeDeposit(int id, PlanType planType, Double balance, int days, List<Withdrawal> withdrawals) {
        this(id, planType, balance, days, withdrawals, 0, 0);
    }

    public TimeDeposit(int id, PlanType planType, Double balance, int days, List<Withdrawal> withdrawals,
                       int lastProcessedDays, long version) {
        if (lastProcessedDays < 0 || version < 0) {
            throw new IllegalArgumentException("Calculation state and version must be non-negative");
        }
        this.id = id;
        this.planType = planType;
        this.balance = new Balance(balance);
        this.days = days;
        this.withdrawals = List.copyOf(withdrawals);
        this.lastProcessedDays = lastProcessedDays;
        this.version = version;
    }

    public int getId() { return id; }

    public PlanType getPlanType() {
        return planType;
    }

    public Double getBalance() {
        return balance.value();
    }

    public int getDays() {
        return days;
    }

    public List<Withdrawal> getWithdrawals() {
        return withdrawals;
    }

    public int getLastProcessedDays() {
        return lastProcessedDays;
    }

    /** The revision loaded with this snapshot, used for compare-and-set persistence. */
    public long getVersion() {
        return version;
    }

    /**
     * The kata has no calendar origin: monthly credits use fixed 30-day periods,
     * starting on day 31 (Basic/Student) or 46 (Premium). The unchanged calculator
     * evaluates eligibility and rounds each individual credit at that period's day.
     */
    public IntStream pendingInterestDays() {
        boolean premium = PlanType.PREMIUM.equals(planType);
        boolean student = PlanType.STUDENT.equals(planType);
        if (!premium && !student && !PlanType.BASIC.equals(planType)) {
            return IntStream.empty();
        }
        int first = premium ? 46 : 31;
        int end = student ? Math.min(days, 365) : days;
        // Long arithmetic avoids overflow near Integer.MAX_VALUE.
        long next = lastProcessedDays < first ? first
                : first + (((long) lastProcessedDays - first) / 30 + 1) * 30;
        int count = next > end ? 0 : (int) (((long) end - next) / 30 + 1);
        return IntStream.range(0, count).map(index -> (int) (next + (long) index * 30));
    }

    /** Also acknowledges ineligible days, without changing the deposit's age. */
    public void markCalculationProcessed() {
        lastProcessedDays = Math.max(lastProcessedDays, days);
    }

    public void setBalance(Double balance) {
        this.balance = new Balance(balance);
    }

    /** Credits already-rounded interest; eligibility and interest rounding belong to the strategy. */
    public void updateBalance(double interest) {
        balance = balance.addInterest(interest);
    }
}
