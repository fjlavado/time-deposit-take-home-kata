package org.ikigaidigital.application.job;

import java.util.UUID;

public record BalanceUpdateJobResult(UUID jobId, Status status) {
    public enum Status { STARTED, RUNNING }
}
