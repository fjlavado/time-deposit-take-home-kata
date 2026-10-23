package org.ikigaidigital.application.port.in;

import org.ikigaidigital.application.job.BalanceUpdateJobResult;

public interface BalanceUpdateJob {
    BalanceUpdateJobResult start();
}
