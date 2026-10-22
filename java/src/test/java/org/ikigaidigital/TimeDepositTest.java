package org.ikigaidigital;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TimeDepositTest {
    @Test
    void updatesTheBalanceWithoutChangingDepositMetadata() {
        TimeDeposit deposit = new TimeDeposit(7, "basic", 1234.567, 31);

        deposit.updateBalance(1.03);

        assertThat(deposit.getBalance()).isEqualTo(1235.597);
        assertThat(deposit.getId()).isEqualTo(7);
        assertThat(deposit.getPlanType()).isEqualTo("basic");
        assertThat(deposit.getDays()).isEqualTo(31);
    }

    @Test
    void repeatedCreditsUseTheCurrentBalanceWithoutRoundingTheResult() {
        TimeDeposit deposit = new TimeDeposit(1, "basic", 1000.0, 31);

        deposit.updateBalance(0.83);
        deposit.updateBalance(0.83);

        assertThat(deposit.getBalance()).isEqualTo(1001.6600000000001);
    }

    @Test
    void keepsTheExistingDoubleGetterAndReplacementSetterCompatible() {
        TimeDeposit deposit = new TimeDeposit(1, "basic", 1000.0, 31);

        deposit.setBalance(2000.123);
        Double replacementBalance = deposit.getBalance();
        deposit.updateBalance(1.0);

        assertThat(replacementBalance).isEqualTo(2000.123);
        assertThat(deposit.getBalance()).isEqualTo(2001.123);
    }

    @Test
    void updatingOneDepositDoesNotAffectAnotherWithTheSameInitialBalance() {
        TimeDeposit first = new TimeDeposit(1, "basic", 1000.0, 31);
        TimeDeposit second = new TimeDeposit(2, "basic", 1000.0, 31);

        first.updateBalance(0.83);

        assertThat(first.getBalance()).isEqualTo(1000.83);
        assertThat(second.getBalance()).isEqualTo(1000.0);
    }
}
