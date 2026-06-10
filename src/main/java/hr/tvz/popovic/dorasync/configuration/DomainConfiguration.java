package hr.tvz.popovic.dorasync.configuration;

import hr.tvz.popovic.dorasync.application.domain.service.ScheduledJobEnqueuer;
import hr.tvz.popovic.dorasync.application.domain.service.StaleJobReaper;
import hr.tvz.popovic.dorasync.application.port.out.AddJobStepPort;
import hr.tvz.popovic.dorasync.application.port.out.FetchScheduledServicesPort;
import hr.tvz.popovic.dorasync.application.port.out.FetchServiceConnectionsPort;
import hr.tvz.popovic.dorasync.application.port.out.ReapStaleJobsPort;
import hr.tvz.popovic.dorasync.application.port.out.RescheduleServicePort;
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
            RescheduleServicePort rescheduleServicePort,
            FetchServiceConnectionsPort fetchServiceConnectionsPort,
            RunJobPort runJobPort,
            AddJobStepPort addJobStepPort
    ) {
        return new ScheduledJobEnqueuer(
                transactionRunnerPort,
                fetchScheduledServicesPort,
                rescheduleServicePort,
                fetchServiceConnectionsPort,
                runJobPort,
                addJobStepPort
        );
    }

    @Bean
    StaleJobReaper staleJobReaper(ReapStaleJobsPort reapStaleJobsPort) {
        return new StaleJobReaper(reapStaleJobsPort);
    }

}
