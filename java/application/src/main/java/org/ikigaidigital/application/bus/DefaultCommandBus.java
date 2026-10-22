package org.ikigaidigital.application.bus;

import org.ikigaidigital.application.command.Command;
import org.ikigaidigital.application.command.handler.CommandHandler;
import org.ikigaidigital.application.port.in.CommandBus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DefaultCommandBus implements CommandBus {
    private final Map<Class<?>, CommandHandler<?, ?>> handlers;

    public DefaultCommandBus(List<CommandHandler<?, ?>> handlers) {
        var registry = new HashMap<Class<?>, CommandHandler<?, ?>>();
        for (var handler : handlers) {
            if (registry.putIfAbsent(handler.commandType(), handler) != null) {
                throw new IllegalArgumentException("Duplicate command handler for " + handler.commandType().getName());
            }
        }
        this.handlers = Map.copyOf(registry);
    }

    @Override
    public <R> R dispatch(Command<R> command) {
        // Registration associates Class<C> with CommandHandler<C, R>; the cast stays at this boundary.
        @SuppressWarnings("unchecked")
        var handler = (CommandHandler<Command<R>, R>) handlers.get(command.getClass());
        if (handler == null) {
            throw new IllegalArgumentException("No command handler for " + command.getClass().getName());
        }
        return handler.handle(command);
    }
}
