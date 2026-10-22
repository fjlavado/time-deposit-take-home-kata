package org.ikigaidigital.domain.interest;

import org.ikigaidigital.domain.model.PlanType;
import org.ikigaidigital.domain.model.TimeDeposit;

/** Additional plans can supply a policy without changing use cases or adapters. */
public interface InterestPolicy {
    boolean supports(PlanType planType);

    double monthlyInterest(TimeDeposit deposit);
}
