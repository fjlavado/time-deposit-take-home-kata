package org.ikigaidigital.application.query;

import org.ikigaidigital.TimeDeposit;
import org.ikigaidigital.domain.model.Page;

public record RetrieveTimeDepositsQuery(int page, int size) implements Query<Page<TimeDeposit>> {
}
