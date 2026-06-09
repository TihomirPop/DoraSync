package hr.tvz.popovic.dorasync.configuration;

import hr.tvz.popovic.dorasync.application.domain.service.ScheduledJobEnqueuer;
import hr.tvz.popovic.dorasync.application.port.out.FetchScheduledServicesPort;
import hr.tvz.popovic.dorasync.application.port.out.RunJobPort;
import hr.tvz.popovic.dorasync.application.port.out.TransactionRunnerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainConfiguration {

    @Bean
    ScheduledJobEnqueuer scheduledJobEnqueuer(
            TransactionRunnerPort transactionRunnerPort,
            FetchScheduledServicesPort fetchScheduledServicesPort,
            RunJobPort runJobPort
    ) {
        return new ScheduledJobEnqueuer(
                transactionRunnerPort,
                fetchScheduledServicesPort,
                runJobPort
        );
    }

}
