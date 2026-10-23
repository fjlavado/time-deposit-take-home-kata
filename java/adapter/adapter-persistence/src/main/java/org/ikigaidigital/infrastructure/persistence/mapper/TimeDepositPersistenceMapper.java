package org.ikigaidigital.infrastructure.persistence.mapper;

import org.ikigaidigital.domain.interest.TimeDeposit;
import org.ikigaidigital.domain.interest.vo.PlanType;
import org.ikigaidigital.domain.model.Withdrawal;
import org.ikigaidigital.infrastructure.persistence.jdbc.TimeDepositTable;
import org.ikigaidigital.infrastructure.persistence.jdbc.WithdrawalTable;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.stream.Collectors;

public final class TimeDepositPersistenceMapper {
    public TimeDepositTable toTable(TimeDeposit deposit) {
        return new TimeDepositTable(deposit.getId(), deposit.getPlanType().value(),
                BigDecimal.valueOf(deposit.getBalance()), deposit.getDays(), deposit.getLastProcessedDays(),
                deposit.getVersion(), deposit.getWithdrawals().stream()
                        .map(withdrawal -> new WithdrawalTable(withdrawal.id(), withdrawal.amount(), withdrawal.date()))
                        .collect(Collectors.toSet()));
    }

    public TimeDeposit toDomain(TimeDepositTable deposit) {
        // Decimal storage meets compatible double arithmetic only at this adapter boundary.
        return new TimeDeposit(deposit.id(), new PlanType(deposit.planType()), deposit.balance().doubleValue(), deposit.days(),
                deposit.withdrawals().stream().sorted(Comparator.comparing(WithdrawalTable::id))
                        .map(row -> new Withdrawal(row.id(), row.amount(), row.date())).toList(),
                deposit.lastProcessedDays(), deposit.version());
    }
}
