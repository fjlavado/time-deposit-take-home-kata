package org.ikigaidigital.services.interest;

import org.ikigaidigital.domain.interest.TimeDeposit;
import org.ikigaidigital.domain.interest.vo.PlanType;

public final class StudentInterestStrategy implements InterestStrategy {

    public static final StudentInterestStrategy STUDENT_INTEREST_STRATEGY = new StudentInterestStrategy();

    private StudentInterestStrategy() {}

    @Override
    public PlanType planType() {
        return PlanType.STUDENT;
    }

    @Override
    public InterestStrategy apply(TimeDeposit deposit) {
        return deposit.getDays() > 30 && deposit.getDays() < 366 ? STUDENT_INTEREST_STRATEGY : NoInterestStrategy.NO_INTEREST_STRATEGY;
    }

    @Override
    public double calculateInterest(TimeDeposit deposit) {
        return deposit.getBalance() * 0.03 / 12;
    }
}
