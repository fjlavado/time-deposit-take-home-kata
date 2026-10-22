package org.ikigaidigital.application.query.handler;

import org.ikigaidigital.application.query.Query;

public interface QueryHandler<Q extends Query<R>, R> {
    Class<Q> queryType();

    R handle(Q query);
}
