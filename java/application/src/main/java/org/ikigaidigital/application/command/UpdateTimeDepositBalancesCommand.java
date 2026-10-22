package org.ikigaidigital.application.command;

/** Every dispatch applies another interest credit; there is no response payload. */
public record UpdateTimeDepositBalancesCommand() implements Command<Void> {
}
