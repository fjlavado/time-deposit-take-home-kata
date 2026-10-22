package org.ikigaidigital.application.query;

import org.ikigaidigital.TimeDeposit;

import java.util.List;

public record RetrieveTimeDepositsQuery() implements Query<List<TimeDeposit>> {
}
