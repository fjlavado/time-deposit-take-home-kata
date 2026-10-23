package org.ikigaidigital.application.query;

import org.ikigaidigital.domain.interest.TimeDeposit;
import org.ikigaidigital.application.pagination.Page;

public record RetrieveTimeDepositsQuery(int page, int size) implements Query<Page<TimeDeposit>> {
}
