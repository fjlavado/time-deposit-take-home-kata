package org.ikigaidigital.infrastructure.configuration;

import org.ikigaidigital.application.bus.DefaultCommandBus;
import org.ikigaidigital.application.bus.DefaultQueryBus;
import org.ikigaidigital.application.command.handler.CommandHandler;
import org.ikigaidigital.application.command.handler.UpdateTimeDepositBalancesCommandHandler;
import org.ikigaidigital.application.port.in.CommandBus;
import org.ikigaidigital.application.port.in.QueryBus;
import org.ikigaidigital.application.query.handler.QueryHandler;
import org.ikigaidigital.application.query.handler.RetrieveTimeDepositsQueryHandler;
import org.ikigaidigital.domain.interest.MonthlyInterestCalculator;
import org.ikigaidigital.domain.port.out.TimeDepositRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@Configuration(proxyBeanMethods = false)
public class UseCaseConfiguration {
    @Bean
    MonthlyInterestCalculator monthlyInterestCalculator() {
        return MonthlyInterestCalculator.standardPlans();
    }

    @Bean
    RetrieveTimeDepositsQueryHandler retrieveTimeDepositsQueryHandler(TimeDepositRepository repository) {
        return new RetrieveTimeDepositsQueryHandler(repository);
    }

    @Bean
    UpdateTimeDepositBalancesCommandHandler updateTimeDepositBalancesCommandHandler(TimeDepositRepository repository,
                                                                                   MonthlyInterestCalculator calculator) {
        return new UpdateTimeDepositBalancesCommandHandler(repository, calculator);
    }

    @Bean
    CommandBus commandBus(List<CommandHandler<?, ?>> handlers, PlatformTransactionManager transactions) {
        return new TransactionalCommandBus(new DefaultCommandBus(handlers), new TransactionTemplate(transactions));
    }

    @Bean
    QueryBus queryBus(List<QueryHandler<?, ?>> handlers) {
        return new DefaultQueryBus(handlers);
    }
}
