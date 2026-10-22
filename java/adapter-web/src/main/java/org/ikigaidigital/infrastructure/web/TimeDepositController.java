package org.ikigaidigital.infrastructure.web;

import org.ikigaidigital.application.port.in.RetrieveTimeDepositsUseCase;
import org.ikigaidigital.application.port.in.UpdateTimeDepositBalancesUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/time-deposits")
public final class TimeDepositController {
    private final RetrieveTimeDepositsUseCase retrieve;
    private final UpdateTimeDepositBalancesUseCase update;

    public TimeDepositController(RetrieveTimeDepositsUseCase retrieve, UpdateTimeDepositBalancesUseCase update) {
        this.retrieve = retrieve;
        this.update = update;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<TimeDepositResponse> retrieveAll() {
        return retrieve.retrieveAll().stream().map(TimeDepositResponse::from).toList();
    }

    @PostMapping("/balance-updates")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateBalances() {
        // Every invocation credits interest again; this command is intentionally non-idempotent.
        update.updateBalances();
    }
}
