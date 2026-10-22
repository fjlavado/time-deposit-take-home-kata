package org.ikigaidigital.infrastructure.configuration;

import org.ikigaidigital.application.port.in.RetrieveTimeDepositsUseCase;
import org.ikigaidigital.application.port.in.UpdateTimeDepositBalancesUseCase;
import org.ikigaidigital.application.service.RetrieveTimeDepositsService;
import org.ikigaidigital.application.service.UpdateTimeDepositBalancesService;
import org.ikigaidigital.domain.interest.MonthlyInterestCalculator;
import org.ikigaidigital.domain.port.out.TimeDepositRepository;
import org.ikigaidigital.infrastructure.persistence.JdbcTimeDepositRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
public class UseCaseConfiguration {
    @Bean
    TimeDepositRepository timeDepositRepository(JdbcTemplate jdbc) {
        return new JdbcTimeDepositRepository(jdbc);
    }

    @Bean
    MonthlyInterestCalculator monthlyInterestCalculator() {
        return MonthlyInterestCalculator.standardPlans();
    }

    @Bean
    RetrieveTimeDepositsUseCase retrieveTimeDeposits(TimeDepositRepository repository) {
        return new RetrieveTimeDepositsService(repository);
    }

    @Bean
    UpdateTimeDepositBalancesUseCase updateTimeDepositBalances(TimeDepositRepository repository,
                                                              MonthlyInterestCalculator calculator,
                                                              PlatformTransactionManager transactions) {
        var service = new UpdateTimeDepositBalancesService(repository, calculator);
        return new TransactionalBalanceUpdate(service, new TransactionTemplate(transactions));
    }
}
