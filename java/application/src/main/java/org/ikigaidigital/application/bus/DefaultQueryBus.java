package org.ikigaidigital.application.bus;

import org.ikigaidigital.application.port.in.QueryBus;
import org.ikigaidigital.application.query.Query;
import org.ikigaidigital.application.query.handler.QueryHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DefaultQueryBus implements QueryBus {
    private final Map<Class<?>, QueryHandler<?, ?>> handlers;

    public DefaultQueryBus(List<QueryHandler<?, ?>> handlers) {
        var registry = new HashMap<Class<?>, QueryHandler<?, ?>>();
        for (var handler : handlers) {
            if (registry.putIfAbsent(handler.queryType(), handler) != null) {
                throw new IllegalArgumentException("Duplicate query handler for " + handler.queryType().getName());
            }
        }
        this.handlers = Map.copyOf(registry);
    }

    @Override
    public <R> R dispatch(Query<R> query) {
        // Registration associates Class<Q> with QueryHandler<Q, R>; the cast stays at this boundary.
        @SuppressWarnings("unchecked")
        var handler = (QueryHandler<Query<R>, R>) handlers.get(query.getClass());
        if (handler == null) {
            throw new IllegalArgumentException("No query handler for " + query.getClass().getName());
        }
        return handler.handle(query);
    }
}
