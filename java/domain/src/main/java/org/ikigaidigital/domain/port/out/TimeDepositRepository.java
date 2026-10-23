package org.ikigaidigital.domain.port.out;

import org.ikigaidigital.domain.interest.TimeDeposit;
import org.ikigaidigital.domain.model.Page;
import org.ikigaidigital.domain.model.PageRequestVO;

public interface TimeDepositRepository {
    Page<TimeDeposit> search(PageRequestVO request);
}
