package org.ikigaidigital.application.port.out;

import org.ikigaidigital.domain.interest.TimeDeposit;
import org.ikigaidigital.application.pagination.Page;
import org.ikigaidigital.application.pagination.PageRequestVO;

public interface TimeDepositRepository {
    Page<TimeDeposit> search(PageRequestVO request);
}
