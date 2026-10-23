package org.ikigaidigital.infrastructure.web;

import org.ikigaidigital.application.command.UpdateTimeDepositBalancesCommand;
import org.ikigaidigital.application.port.in.CommandBus;
import org.ikigaidigital.application.port.in.QueryBus;
import org.ikigaidigital.application.query.RetrieveTimeDepositsQuery;
import org.ikigaidigital.infrastructure.web.generated.api.TimeDepositsApi;
import org.ikigaidigital.infrastructure.web.generated.model.TimeDepositPageResponse;
import org.ikigaidigital.infrastructure.web.mapper.TimeDepositApiMapper;
import org.springframework.web.bind.annotation.RestController;

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
    public TimeDepositPageResponse retrieveTimeDeposits(Integer page, Integer size) {
        var result = queryBus.dispatch(new RetrieveTimeDepositsQuery(page, size));
        return new TimeDepositPageResponse(result.content().stream().map(mapper::toResponse).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages());
    }

    @Override
    public void updateTimeDepositBalances() {
        // Only unprocessed monthly periods are credited; the transaction completes before 204.
        commandBus.dispatch(new UpdateTimeDepositBalancesCommand());
    }
}
