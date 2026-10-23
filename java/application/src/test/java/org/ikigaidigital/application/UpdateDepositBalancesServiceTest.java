package org.ikigaidigital.application;

import org.ikigaidigital.application.service.UpdateDepositBalancesService;
import org.ikigaidigital.domain.interest.TimeDeposit;
import org.ikigaidigital.domain.interest.vo.PlanType;
import org.ikigaidigital.domain.port.out.TimeDepositRepositoryPort;
import org.ikigaidigital.services.TimeDepositCalculator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UpdateDepositBalancesServiceTest {
    @Test
    void catchesUpMissedPeriodsAndDoesNotReapplyPreviouslyProcessedInterest() {
        var deposits = List.of(deposit(1, PlanType.BASIC, 91, 31),
                deposit(2, PlanType.PREMIUM, 76, 0), deposit(3, PlanType.STUDENT, 400, 331),
                deposit(4, PlanType.BASIC, 30, 0));
        var saved = new ArrayList<TimeDeposit>();
        var repository = new TimeDepositRepositoryPort() {
            public Stream<TimeDeposit> streamPending() {
                return deposits.stream().filter(d -> d.getDays() > d.getLastProcessedDays());
            }
            public void saveBatch(List<TimeDeposit> batch) { saved.addAll(batch); }
        };
        var service = new UpdateDepositBalancesService(repository, new TimeDepositCalculator());

        service.updateBalances();
        // Preserve the calculator's unrounded final binary-double balance.
        assertThat(deposits).extracting(TimeDeposit::getBalance)
                .containsExactly(1001.6600000000001, 1008.3499999999999, 1002.5, 1000.0);
        assertThat(deposits).extracting(TimeDeposit::getLastProcessedDays).containsExactly(91, 76, 400, 30);
        service.updateBalances();
        assertThat(saved).containsExactlyElementsOf(deposits);
    }

    @Test
    void consumesLazilyAndFlushesFullAndPartialBatches() {
        var read = new AtomicInteger();
        var closed = new AtomicBoolean();
        var sizes = new ArrayList<Integer>();
        var repository = new TimeDepositRepositoryPort() {
            public Stream<TimeDeposit> streamPending() {
                return IntStream.rangeClosed(1, 205).mapToObj(id -> deposit(id, PlanType.BASIC, 31, 0))
                        .peek(d -> read.incrementAndGet()).onClose(() -> closed.set(true));
            }
            public void saveBatch(List<TimeDeposit> batch) {
                sizes.add(batch.size());
                assertThat(read.get()).isEqualTo(sizes.stream().mapToInt(Integer::intValue).sum());
                assertThat(batch).extracting(TimeDeposit::getBalance).containsOnly(1000.83);
            }
        };

        new UpdateDepositBalancesService(repository, new TimeDepositCalculator()).updateBalances();
        assertThat(sizes).containsExactly(100, 100, 5);
        assertThat(closed).isTrue();
    }

    @Test
    void closesTheStreamAndPropagatesWriteFailures() {
        var closed = new AtomicBoolean();
        var repository = new TimeDepositRepositoryPort() {
            public Stream<TimeDeposit> streamPending() {
                return Stream.of(deposit(1, PlanType.BASIC, 31, 0)).onClose(() -> closed.set(true));
            }
            public void saveBatch(List<TimeDeposit> batch) { throw new IllegalStateException("write failed"); }
        };

        assertThatThrownBy(() -> new UpdateDepositBalancesService(repository, new TimeDepositCalculator()).updateBalances())
                .isInstanceOf(IllegalStateException.class).hasMessage("write failed");
        assertThat(closed).isTrue();
    }

    private static TimeDeposit deposit(int id, PlanType plan, int days, int processed) {
        return new TimeDeposit(id, plan, 1000.0, days, List.of(), processed, 0);
    }
}
