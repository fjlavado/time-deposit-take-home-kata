package org.ikigaidigital.application.port.in;

import org.ikigaidigital.application.query.Query;

public interface QueryBus {
    <R> R dispatch(Query<R> query);
}
