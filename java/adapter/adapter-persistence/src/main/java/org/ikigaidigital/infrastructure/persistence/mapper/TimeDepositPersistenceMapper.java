package org.ikigaidigital.infrastructure.persistence.mapper;

import org.ikigaidigital.domain.interest.TimeDeposit;
import org.ikigaidigital.domain.interest.vo.PlanType;
import org.ikigaidigital.domain.model.Withdrawal;
import org.ikigaidigital.infrastructure.persistence.jdbc.TimeDepositTable;
import org.ikigaidigital.infrastructure.persistence.jdbc.WithdrawalTable;

import java.util.Comparator;

public final class TimeDepositPersistenceMapper {
    public TimeDeposit toDomain(TimeDepositTable deposit) {
        // Decimal storage meets compatible double arithmetic only at this adapter boundary.
        return new TimeDeposit(deposit.id(), new PlanType(deposit.planType()), deposit.balance().doubleValue(), deposit.days(),
                deposit.withdrawals().stream().sorted(Comparator.comparing(WithdrawalTable::id))
                        .map(row -> new Withdrawal(row.id(), row.amount(), row.date())).toList(),
                deposit.lastProcessedDays(), deposit.version());
    }
}
