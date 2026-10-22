package org.ikigaidigital.application.port.in;

import org.ikigaidigital.application.command.Command;

public interface CommandBus {
    <R> R dispatch(Command<R> command);
}
