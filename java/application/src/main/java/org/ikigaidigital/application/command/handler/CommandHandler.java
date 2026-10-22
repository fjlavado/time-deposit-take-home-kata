package org.ikigaidigital.application.command.handler;

import org.ikigaidigital.application.command.Command;

public interface CommandHandler<C extends Command<R>, R> {
    Class<C> commandType();

    R handle(C command);
}
