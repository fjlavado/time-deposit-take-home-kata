package org.ikigaidigital.domain.model;

/** Retains the exact stored value: unknown and differently-cased names earn no interest. */
public record PlanType(String value) {
    public static final PlanType BASIC = new PlanType("basic");
    public static final PlanType STUDENT = new PlanType("student");
    public static final PlanType PREMIUM = new PlanType("premium");
}
