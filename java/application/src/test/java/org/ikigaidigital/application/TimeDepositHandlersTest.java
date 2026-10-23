package org.ikigaidigital.application;

import org.ikigaidigital.domain.interest.TimeDeposit;
import org.ikigaidigital.domain.interest.vo.PlanType;
import org.ikigaidigital.services.TimeDepositCalculator;
import org.ikigaidigital.application.bus.DefaultCommandBus;
import org.ikigaidigital.application.bus.DefaultQueryBus;
import org.ikigaidigital.application.command.UpdateTimeDepositBalancesCommand;
import org.ikigaidigital.application.command.handler.UpdateTimeDepositBalancesCommandHandler;
import org.ikigaidigital.application.query.RetrieveTimeDepositsQuery;
import org.ikigaidigital.application.query.handler.RetrieveTimeDepositsQueryHandler;
import org.ikigaidigital.domain.model.Withdrawal;
import org.ikigaidigital.domain.model.Page;
import org.ikigaidigital.domain.model.PageRequest;
import org.ikigaidigital.domain.port.out.TimeDepositRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class TimeDepositHandlersTest {
    @Test
    void retrievesDepositsIncludingWithdrawalsWithoutUpdatingThem() {
        var deposits = List.of(deposit(1, "basic", 31));
        var repository = new InMemoryRepository(deposits);

        var result = queryBus(repository).dispatch(new RetrieveTimeDepositsQuery(0, 20));
        assertThat(result.content()).isEqualTo(deposits);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(repository.saveCalls).isZero();
        assertThat(repository.lockCalls).isZero();
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
        assertThat(repository.lockCalls).isEqualTo(1);
        assertThat(repository.saveCalls).isEqualTo(1);
    }

    @Test
    void supportsAnEmptyRepositoryForBothOperations() {
        var repository = new InMemoryRepository(List.of());

        commandBus(repository).dispatch(new UpdateTimeDepositBalancesCommand());

        var page = queryBus(repository).dispatch(new RetrieveTimeDepositsQuery(0, 20));
        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isZero();
        assertThat(page.totalPages()).isZero();
        assertThat(repository.saveCalls).isEqualTo(1);
    }

    @Test
    void retrievesOnlyTheRequestedPageWithTotals() {
        var repository = new InMemoryRepository(IntStream.rangeClosed(1, 5)
                .mapToObj(id -> deposit(id, "basic", 31)).toList());

        var page = queryBus(repository).dispatch(new RetrieveTimeDepositsQuery(1, 2));

        assertThat(page.content()).extracting(TimeDeposit::getId).containsExactly(3, 4);
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(2);
        assertThat(page.totalElements()).isEqualTo(5);
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.hasNext()).isTrue();
        assertThat(repository.lockCalls).isZero();
        assertThat(repository.saveCalls).isZero();
    }

    @Test
    void updatesEveryPageExactlyOnceAfterLocking() {
        var repository = new InMemoryRepository(IntStream.rangeClosed(1, 201)
                .mapToObj(id -> deposit(id, "basic", 31)).toList());

        commandBus(repository).dispatch(new UpdateTimeDepositBalancesCommand());

        assertThat(repository.deposits).extracting(TimeDeposit::getBalance).containsOnly(1000.83);
        assertThat(repository.savedIds).containsExactlyElementsOf(IntStream.rangeClosed(1, 201).boxed().toList());
        assertThat(repository.searches).containsExactly(new PageRequest(0, 100),
                new PageRequest(1, 100), new PageRequest(2, 100));
        assertThat(repository.lockCalls).isEqualTo(1);
        assertThat(repository.saveCalls).isEqualTo(3);
    }

    private static DefaultCommandBus commandBus(TimeDepositRepository repository) {
        return new DefaultCommandBus(List.of(new UpdateTimeDepositBalancesCommandHandler(repository,
                new TimeDepositCalculator())));
    }

    private static DefaultQueryBus queryBus(TimeDepositRepository repository) {
        return new DefaultQueryBus(List.of(new RetrieveTimeDepositsQueryHandler(repository)));
    }

    private static TimeDeposit deposit(int id, String plan, int days) {
        return new TimeDeposit(id, new PlanType(plan), 1000.0, days,
                List.of(new Withdrawal(id, new BigDecimal("12.34"), LocalDate.of(2026, 1, 2))));
    }

    private record DepositMetadata(int id, PlanType planType, int days, List<Withdrawal> withdrawals) {
        static DepositMetadata snapshot(TimeDeposit deposit) {
            return new DepositMetadata(deposit.getId(), deposit.getPlanType(), deposit.getDays(),
                    List.copyOf(deposit.getWithdrawals()));
        }
    }

    private static final class InMemoryRepository implements TimeDepositRepository {
        private final List<TimeDeposit> deposits;
        private final List<Integer> savedIds = new ArrayList<>();
        private final List<PageRequest> searches = new ArrayList<>();
        private int saveCalls;
        private int lockCalls;

        private InMemoryRepository(List<TimeDeposit> deposits) {
            this.deposits = List.copyOf(deposits);
        }

        @Override
        public Page<TimeDeposit> search(PageRequest request) {
            searches.add(request);
            int start = (int) Math.min((long) request.page() * request.size(), deposits.size());
            int end = Math.min(start + request.size(), deposits.size());
            return new Page<>(deposits.subList(start, end), request.page(), request.size(), deposits.size());
        }

        @Override
        public void lockForBalanceUpdate() {
            lockCalls++;
        }

        @Override
        public void saveBalances(List<TimeDeposit> updated) {
            assertThat(lockCalls).isEqualTo(1);
            savedIds.addAll(updated.stream().map(TimeDeposit::getId).toList());
            saveCalls++;
        }
    }
}
