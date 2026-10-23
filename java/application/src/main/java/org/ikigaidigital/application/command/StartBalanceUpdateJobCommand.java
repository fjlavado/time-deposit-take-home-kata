package org.ikigaidigital.application.command;

import org.ikigaidigital.application.job.BalanceUpdateJobResult;

public record StartBalanceUpdateJobCommand() implements Command<BalanceUpdateJobResult> {
}
