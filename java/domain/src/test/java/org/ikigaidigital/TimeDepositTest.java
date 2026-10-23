package org.ikigaidigital;

import org.ikigaidigital.domain.interest.TimeDeposit;
import org.ikigaidigital.domain.interest.vo.PlanType;
import org.ikigaidigital.domain.model.Withdrawal;
import org.ikigaidigital.services.TimeDepositCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeDepositTest {
    @Test
    void fourArgumentConstructorDefaultsToNoWithdrawals() {
        var deposit = new TimeDeposit(1, PlanType.BASIC, 1000.0, 31);

        assertThat(deposit.getWithdrawals()).isEmpty();
    }

    @Test
    void snapshotsWithdrawalsAndKeepsThemReadOnlyAcrossBalanceChanges() {
        var withdrawal = new Withdrawal(2, new BigDecimal("2.50"), LocalDate.of(2026, 1, 2));
        var withdrawals = new ArrayList<>(List.of(withdrawal));
        var deposit = new TimeDeposit(1, PlanType.BASIC, 12.345, 31, withdrawals);
        withdrawals.clear();

        new TimeDepositCalculator().updateBalance(List.of(deposit));

        assertThat(deposit.getBalance()).isEqualTo(12.345 + 0.01);
        assertThat(deposit.getId()).isEqualTo(1);
        assertThat(deposit.getPlanType()).isEqualTo(PlanType.BASIC);
        assertThat(deposit.getDays()).isEqualTo(31);

        final List<Withdrawal> withdrawals1 = deposit.getWithdrawals();
        assertThat(withdrawals1).containsExactly(withdrawal);
        assertThatThrownBy(withdrawals1::clear)
                .isInstanceOf(UnsupportedOperationException.class);

        deposit.setBalance(2000.0);
        assertThat(withdrawals1).containsExactly(withdrawal);
    }

    @Test
    void updatesTheBalanceWithoutChangingDepositMetadata() {
        TimeDeposit deposit = new TimeDeposit(7, PlanType.BASIC, 1234.567, 31);

        deposit.updateBalance(1.03);

        assertThat(deposit.getBalance()).isEqualTo(1235.597);
        assertThat(deposit.getId()).isEqualTo(7);
        assertThat(deposit.getPlanType()).isEqualTo(PlanType.BASIC);
        assertThat(deposit.getDays()).isEqualTo(31);
    }

    @Test
    void repeatedCreditsUseTheCurrentBalanceWithoutRoundingTheResult() {
        TimeDeposit deposit = new TimeDeposit(1, PlanType.BASIC, 1000.0, 31);

        deposit.updateBalance(0.83);
        deposit.updateBalance(0.83);

        assertThat(deposit.getBalance()).isEqualTo(1001.6600000000001);
    }

    @Test
    void keepsTheExistingDoubleGetterAndReplacementSetterCompatible() {
        TimeDeposit deposit = new TimeDeposit(1, PlanType.BASIC, 1000.0, 31);

        deposit.setBalance(2000.123);
        Double replacementBalance = deposit.getBalance();
        deposit.updateBalance(1.0);

        assertThat(replacementBalance).isEqualTo(2000.123);
        assertThat(deposit.getBalance()).isEqualTo(2001.123);
    }

    @Test
    void updatingOneDepositDoesNotAffectAnotherWithTheSameInitialBalance() {
        TimeDeposit first = new TimeDeposit(1, PlanType.BASIC, 1000.0, 31);
        TimeDeposit second = new TimeDeposit(2, PlanType.BASIC, 1000.0, 31);

        first.updateBalance(0.83);

        assertThat(first.getBalance()).isEqualTo(1000.83);
        assertThat(second.getBalance()).isEqualTo(1000.0);
    }
}
