package org.ikigaidigital.infrastructure.web;

import org.ikigaidigital.application.command.UpdateTimeDepositBalancesCommand;
import org.ikigaidigital.application.port.in.CommandBus;
import org.ikigaidigital.application.port.in.QueryBus;
import org.ikigaidigital.application.query.RetrieveTimeDepositsQuery;
import org.ikigaidigital.infrastructure.web.generated.api.TimeDepositsApi;
import org.ikigaidigital.infrastructure.web.generated.model.TimeDepositResponse;
import org.ikigaidigital.infrastructure.web.mapper.TimeDepositApiMapper;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public final class TimeDepositController implements TimeDepositsApi {
    private final CommandBus commandBus;
    private final QueryBus queryBus;
    private final TimeDepositApiMapper mapper;

    public TimeDepositController(CommandBus commandBus, QueryBus queryBus, TimeDepositApiMapper mapper) {
        this.commandBus = commandBus;
        this.queryBus = queryBus;
        this.mapper = mapper;
    }

    @Override
    public List<TimeDepositResponse> retrieveTimeDeposits() {
        return queryBus.dispatch(new RetrieveTimeDepositsQuery()).stream().map(mapper::toResponse).toList();
    }

    @Override
    public void updateTimeDepositBalances() {
        // Every invocation credits interest again; this command is intentionally non-idempotent.
        commandBus.dispatch(new UpdateTimeDepositBalancesCommand());
    }
}
