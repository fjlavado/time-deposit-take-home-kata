package org.ikigaidigital.application.query;

import org.ikigaidigital.domain.model.TimeDeposit;

import java.util.List;

public record RetrieveTimeDepositsQuery() implements Query<List<TimeDeposit>> {
}
