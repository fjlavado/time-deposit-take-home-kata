package org.ikigaidigital.domain.model;

import java.util.List;

/** Balance deliberately retains the starter's double arithmetic; decimal storage belongs to the adapter. */
public record TimeDeposit(int id, PlanType planType, double balance, int days, List<Withdrawal> withdrawals) {
    public TimeDeposit {
        withdrawals = List.copyOf(withdrawals);
    }

    public TimeDeposit withBalance(double updatedBalance) {
        return new TimeDeposit(id, planType, updatedBalance, days, withdrawals);
    }
}
