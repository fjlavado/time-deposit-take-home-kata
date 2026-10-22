package org.ikigaidigital;

/** Immutable balance value; retains the starter's double arithmetic without final-balance rounding. */
public record Balance(double amount) {
    public Balance addInterest(double interest) {
        return new Balance(amount + interest);
    }
}
