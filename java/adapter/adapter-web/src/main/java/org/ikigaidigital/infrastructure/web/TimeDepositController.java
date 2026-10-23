package org.ikigaidigital.infrastructure.web;

import org.ikigaidigital.application.command.StartBalanceUpdateJobCommand;
import org.ikigaidigital.application.job.BalanceUpdateJobResult;
import org.ikigaidigital.application.port.in.CommandBus;
import org.ikigaidigital.application.port.in.QueryBus;
import org.ikigaidigital.application.query.RetrieveTimeDepositsQuery;
import org.ikigaidigital.infrastructure.web.generated.api.TimeDepositsApi;
import org.ikigaidigital.infrastructure.web.generated.model.TimeDepositPageResponse;
import org.ikigaidigital.infrastructure.web.generated.model.BalanceUpdateJobResponse;
import org.ikigaidigital.infrastructure.web.mapper.TimeDepositApiMapper;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;

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
    public ResponseEntity<TimeDepositPageResponse> retrieveTimeDeposits(Integer page, Integer size) {
        var result = queryBus.dispatch(new RetrieveTimeDepositsQuery(page, size));
        return ResponseEntity.ok(new TimeDepositPageResponse(result.content().stream().map(mapper::toResponse).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages()));
    }

    @Override
    public ResponseEntity<BalanceUpdateJobResponse> updateTimeDepositBalances() {
        var result = commandBus.dispatch(new StartBalanceUpdateJobCommand());
        var started = result.status() == BalanceUpdateJobResult.Status.STARTED;
        var body = new BalanceUpdateJobResponse(result.jobId(),
                BalanceUpdateJobResponse.StatusEnum.valueOf(result.status().name()),
                started ? "Balance update job accepted." : "A balance update job is already running.");
        return ResponseEntity.status(started ? 202 : 409).body(body);
    }
}
