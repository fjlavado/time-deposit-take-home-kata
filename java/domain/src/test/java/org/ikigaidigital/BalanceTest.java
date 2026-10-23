package org.ikigaidigital;

import org.ikigaidigital.domain.interest.vo.Balance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class BalanceTest {
    @Test
    void equalAmountsHaveValueEqualityAndEqualHashCodes() {
        Balance first = new Balance(1234.567);
        Balance second = new Balance(1234.567);

        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
        assertThat(first).isNotEqualTo(new Balance(1234.568));
    }

    @Test
    void addingInterestReturnsANewValueWithoutChangingTheOriginal() {
        Balance original = new Balance(1000.0);

        Balance credited = original.addInterest(0.83);

        assertThat(original.value()).isEqualTo(1000.0);
        assertThat(credited.value()).isEqualTo(1000.83);
        assertThat(credited).isNotSameAs(original);
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0, 0",
            "1234.567, 1.03, 1235.597",
            "1000.83, 0.83, 1001.6600000000001",
            "1200000000, 1000000, 1201000000"
    })
    void addsInterestWithoutRoundingOrChangingDoubleArithmetic(double amount, double interest, double expected) {
        assertThat(new Balance(amount).addInterest(interest).value()).isEqualTo(expected);
    }
}
