package org.ikigaidigital.application.service;

import org.ikigaidigital.domain.interest.MonthlyInterestCalculator;
import org.ikigaidigital.domain.model.PlanType;
import org.ikigaidigital.domain.model.TimeDeposit;
import org.ikigaidigital.domain.model.Withdrawal;
import org.ikigaidigital.domain.port.out.TimeDepositRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TimeDepositServicesTest {
    @Test
    void retrievesDepositsIncludingWithdrawalsWithoutUpdatingThem() {
        var deposits = List.of(deposit(1, PlanType.BASIC, 31));
        var repository = new InMemoryRepository(deposits);

        assertThat(new RetrieveTimeDepositsService(repository).retrieveAll()).isEqualTo(deposits);
        assertThat(repository.saveCalls).isZero();
        assertThat(repository.updateReads).isZero();
    }

    @Test
    void updatesEveryDepositAndSavesTheResultsTogether() {
        var original = List.of(deposit(1, PlanType.BASIC, 31), deposit(2, PlanType.STUDENT, 365),
                deposit(3, PlanType.PREMIUM, 46), deposit(4, PlanType.STUDENT, 366));
        var repository = new InMemoryRepository(original);
        var service = new UpdateTimeDepositBalancesService(repository, MonthlyInterestCalculator.standardPlans());

        service.updateBalances();

        assertThat(repository.deposits).extracting(TimeDeposit::balance)
                .containsExactly(1000.83, 1002.5, 1004.17, 1000.0);
        for (int i = 0; i < original.size(); i++) {
            assertThat(repository.deposits.get(i)).usingRecursiveComparison().ignoringFields("balance")
                    .isEqualTo(original.get(i));
        }
        assertThat(repository.updateReads).isEqualTo(1);
        assertThat(repository.saveCalls).isEqualTo(1);
    }

    @Test
    void supportsAnEmptyRepositoryForBothOperations() {
        var repository = new InMemoryRepository(List.of());

        new UpdateTimeDepositBalancesService(repository, MonthlyInterestCalculator.standardPlans()).updateBalances();

        assertThat(new RetrieveTimeDepositsService(repository).retrieveAll()).isEmpty();
        assertThat(repository.saveCalls).isEqualTo(1);
    }

    private static TimeDeposit deposit(int id, PlanType plan, int days) {
        return new TimeDeposit(id, plan, 1000, days,
                List.of(new Withdrawal(id, new BigDecimal("12.34"), LocalDate.of(2026, 1, 2))));
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
