package org.ikigaidigital;

import org.ikigaidigital.domain.model.Withdrawal;

import java.util.List;

public class TimeDeposit {
    private final int id;
    private final String planType;
    private Balance balance;
    private final int days;
    private final List<Withdrawal> withdrawals;

    public TimeDeposit(int id, String planType, Double balance, int days) {
        this(id, planType, balance, days, List.of());
    }

    public TimeDeposit(int id, String planType, Double balance, int days, List<Withdrawal> withdrawals) {
        this.id = id;
        this.planType = planType;
        this.balance = new Balance(balance);
        this.days = days;
        this.withdrawals = List.copyOf(withdrawals);
    }

    public int getId() { return id; }

    public String getPlanType() {
        return planType;
    }

    public Double getBalance() {
        return balance.amount();
    }

    public int getDays() {
        return days;
    }

    public List<Withdrawal> getWithdrawals() {
        return withdrawals;
    }

    public void setBalance(Double balance) {
        this.balance = new Balance(balance);
    }

    /** Credits already-rounded interest; eligibility and interest rounding belong to the strategy. */
    public void updateBalance(double interest) {
        balance = balance.addInterest(interest);
    }
}
