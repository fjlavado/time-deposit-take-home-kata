package org.ikigaidigital.domain.interest.vo;

import java.util.Objects;

public record PlanType(String value) implements ValueObject<String> {


    public PlanType {
        Objects.requireNonNull(value);
    }

    public static final PlanType BASIC = new PlanType("basic");
    public static final PlanType STUDENT = new PlanType("student");
    public static final PlanType PREMIUM = new PlanType("premium");


    public static final PlanType UNKNOWN = new PlanType("");


}
