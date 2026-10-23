package org.ikigaidigital.domain.interest.vo;

/** Immutable balance value; retains the starter's double arithmetic without final-balance rounding. */
public record Balance(Double value) implements ValueObject<Double>{
    public Balance addInterest(double interest) {
        return new Balance(value + interest);
    }
}
