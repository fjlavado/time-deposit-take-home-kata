package org.ikigaidigital.domain.interest.vo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class PlanTypeTest {
    @ParameterizedTest
    @ValueSource(strings = {"basic", "student", "premium", "", "FuturePlan", " basic", "premium "})
    void retainsTheExactPlanNameWithValueEquality(String name) {
        PlanType first = new PlanType(name);
        PlanType second = new PlanType(name);

        assertThat(first.value()).isEqualTo(name);
        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }

    @Test
    void constantsMatchTheirExactNames() {
        assertThat(PlanType.BASIC).isEqualTo(new PlanType("basic"));
        assertThat(PlanType.STUDENT).isEqualTo(new PlanType("student"));
        assertThat(PlanType.PREMIUM).isEqualTo(new PlanType("premium"));
        assertThat(PlanType.UNKNOWN).isEqualTo(new PlanType(""));
        assertThat(PlanType.BASIC).isNotEqualTo(new PlanType("Basic"));
        assertThat(PlanType.UNKNOWN).isNotEqualTo(new PlanType("unknown"));
    }

    @Test
    void rejectsANullPlanName() {
        assertThatNullPointerException().isThrownBy(() -> new PlanType(null));
    }
}
