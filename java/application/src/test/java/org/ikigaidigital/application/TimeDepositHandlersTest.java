package org.ikigaidigital.application;

import org.ikigaidigital.TimeDeposit;
import org.ikigaidigital.TimeDepositCalculator;
import org.ikigaidigital.application.bus.DefaultCommandBus;
import org.ikigaidigital.application.bus.DefaultQueryBus;
import org.ikigaidigital.application.command.UpdateTimeDepositBalancesCommand;
import org.ikigaidigital.application.command.handler.UpdateTimeDepositBalancesCommandHandler;
import org.ikigaidigital.application.query.RetrieveTimeDepositsQuery;
import org.ikigaidigital.application.query.handler.RetrieveTimeDepositsQueryHandler;
import org.ikigaidigital.domain.model.Withdrawal;
import org.ikigaidigital.domain.port.out.TimeDepositRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TimeDepositHandlersTest {
    @Test
    void retrievesDepositsIncludingWithdrawalsWithoutUpdatingThem() {
        var deposits = List.of(deposit(1, "basic", 31));
        var repository = new InMemoryRepository(deposits);

        assertThat(queryBus(repository).dispatch(new RetrieveTimeDepositsQuery())).isEqualTo(deposits);
        assertThat(repository.saveCalls).isZero();
        assertThat(repository.updateReads).isZero();
        assertThat(deposits.getFirst().getBalance()).isEqualTo(1000.0);
        assertThat(deposits.getFirst().getWithdrawals()).hasSize(1);
    }

    @Test
    void updatesEveryDepositAndSavesTheResultsTogether() {
        var original = List.of(deposit(1, "basic", 31), deposit(2, "student", 365),
                deposit(3, "premium", 46), deposit(4, "student", 366));
        var metadata = original.stream().map(DepositMetadata::snapshot).toList();
        var repository = new InMemoryRepository(original);
        commandBus(repository).dispatch(new UpdateTimeDepositBalancesCommand());

        assertThat(repository.deposits).extracting(TimeDeposit::getBalance)
                .containsExactly(1000.83, 1002.5, 1004.17, 1000.0);
        for (int i = 0; i < original.size(); i++) {
            assertThat(repository.deposits.get(i)).isSameAs(original.get(i));
            assertThat(DepositMetadata.snapshot(repository.deposits.get(i))).isEqualTo(metadata.get(i));
        }
        assertThat(repository.updateReads).isEqualTo(1);
        assertThat(repository.saveCalls).isEqualTo(1);
    }

    @Test
    void supportsAnEmptyRepositoryForBothOperations() {
        var repository = new InMemoryRepository(List.of());

        commandBus(repository).dispatch(new UpdateTimeDepositBalancesCommand());

        assertThat(queryBus(repository).dispatch(new RetrieveTimeDepositsQuery())).isEmpty();
        assertThat(repository.saveCalls).isEqualTo(1);
    }

    private static DefaultCommandBus commandBus(TimeDepositRepository repository) {
        return new DefaultCommandBus(List.of(new UpdateTimeDepositBalancesCommandHandler(repository,
                new TimeDepositCalculator())));
    }

    private static DefaultQueryBus queryBus(TimeDepositRepository repository) {
        return new DefaultQueryBus(List.of(new RetrieveTimeDepositsQueryHandler(repository)));
    }

    private static TimeDeposit deposit(int id, String plan, int days) {
        return new TimeDeposit(id, plan, 1000.0, days,
                List.of(new Withdrawal(id, new BigDecimal("12.34"), LocalDate.of(2026, 1, 2))));
    }

    private record DepositMetadata(int id, String planType, int days, List<Withdrawal> withdrawals) {
        static DepositMetadata snapshot(TimeDeposit deposit) {
            return new DepositMetadata(deposit.getId(), deposit.getPlanType(), deposit.getDays(),
                    List.copyOf(deposit.getWithdrawals()));
        }
    }

    private static final class InMemoryRepository implements TimeDepositRepository {
        private List<TimeDeposit> deposits;
        private int saveCalls;
        private int updateReads;

        private InMemoryRepository(List<TimeDeposit> deposits) {
            this.deposits = List.copyOf(deposits);
        }

        @Override
        public List<TimeDeposit> findAll() {
            return deposits;
        }

        @Override
        public List<TimeDeposit> findAllForBalanceUpdate() {
            updateReads++;
            return deposits;
        }

        @Override
        public void saveBalances(List<TimeDeposit> updated) {
            deposits = List.copyOf(updated);
            saveCalls++;
        }
    }
}
