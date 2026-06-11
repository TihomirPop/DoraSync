package hr.tvz.popovic.dorasync.configuration;

import hr.tvz.popovic.dorasync.application.domain.service.DeploykoStepCollector;
import hr.tvz.popovic.dorasync.application.domain.service.GithubStepCollector;
import hr.tvz.popovic.dorasync.application.domain.service.JenkinsStepCollector;
import hr.tvz.popovic.dorasync.application.domain.service.JobStepWorker;
import hr.tvz.popovic.dorasync.application.domain.service.ScheduledJobEnqueuer;
import hr.tvz.popovic.dorasync.application.domain.service.StaleJobReaper;
import hr.tvz.popovic.dorasync.application.port.out.AddJobStepPort;
import hr.tvz.popovic.dorasync.application.port.out.DeploykoRepositoryPort;
import hr.tvz.popovic.dorasync.application.port.out.DequeueJobStepsPort;
import hr.tvz.popovic.dorasync.application.port.out.FetchConnectionPort;
import hr.tvz.popovic.dorasync.application.port.out.FetchDeploykoDeploymentsPort;
import hr.tvz.popovic.dorasync.application.port.out.FetchGithubHistoryPort;
import hr.tvz.popovic.dorasync.application.port.out.FetchJenkinsBuildsPort;
import hr.tvz.popovic.dorasync.application.port.out.GithubRepositoryPort;
import hr.tvz.popovic.dorasync.application.port.out.JenkinsRepositoryPort;
import hr.tvz.popovic.dorasync.application.port.out.FetchScheduledServicesPort;
import hr.tvz.popovic.dorasync.application.port.out.FetchServiceConnectionsPort;
import hr.tvz.popovic.dorasync.application.port.out.FinishJobPort;
import hr.tvz.popovic.dorasync.application.port.out.FinishJobStepPort;
import hr.tvz.popovic.dorasync.application.port.out.ReapStaleJobsPort;
import hr.tvz.popovic.dorasync.application.port.out.RescheduleServicePort;
import hr.tvz.popovic.dorasync.application.port.out.RunJobPort;
import hr.tvz.popovic.dorasync.application.port.out.TaskExecutorPort;
import hr.tvz.popovic.dorasync.application.port.out.TransactionRunnerPort;
import org.springframework.beans.factory.annotation.Value;
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
    StaleJobReaper staleJobReaper(TransactionRunnerPort transactionRunnerPort, ReapStaleJobsPort reapStaleJobsPort) {
        return new StaleJobReaper(transactionRunnerPort, reapStaleJobsPort);
    }

    @Bean
    GithubStepCollector githubStepCollector(
            FetchConnectionPort fetchConnectionPort,
            FetchGithubHistoryPort fetchGithubHistoryPort,
            GithubRepositoryPort githubRepositoryPort,
            TransactionRunnerPort transactionRunnerPort
    ) {
        return new GithubStepCollector(
                fetchConnectionPort,
                fetchGithubHistoryPort,
                githubRepositoryPort,
                transactionRunnerPort
        );
    }

    @Bean
    JenkinsStepCollector jenkinsStepCollector(
            FetchConnectionPort fetchConnectionPort,
            FetchJenkinsBuildsPort fetchJenkinsBuildsPort,
            JenkinsRepositoryPort jenkinsRepositoryPort,
            TransactionRunnerPort transactionRunnerPort
    ) {
        return new JenkinsStepCollector(
                fetchConnectionPort,
                fetchJenkinsBuildsPort,
                jenkinsRepositoryPort,
                transactionRunnerPort
        );
    }

    @Bean
    DeploykoStepCollector deploykoStepCollector(
            FetchConnectionPort fetchConnectionPort,
            FetchDeploykoDeploymentsPort fetchDeploykoDeploymentsPort,
            DeploykoRepositoryPort deploykoRepositoryPort,
            TransactionRunnerPort transactionRunnerPort
    ) {
        return new DeploykoStepCollector(
                fetchConnectionPort,
                fetchDeploykoDeploymentsPort,
                deploykoRepositoryPort,
                transactionRunnerPort
        );
    }

    @Bean
    JobStepWorker jobStepWorker(
            @Value("${dora-sync.job-step-worker.batch-size}") int batchSize,
            TransactionRunnerPort transactionRunnerPort,
            DequeueJobStepsPort dequeueJobStepsPort,
            FinishJobStepPort finishJobStepPort,
            FinishJobPort finishJobPort,
            TaskExecutorPort taskExecutorPort,
            GithubStepCollector githubStepCollector,
            JenkinsStepCollector jenkinsStepCollector,
            DeploykoStepCollector deploykoStepCollector
    ) {
        return new JobStepWorker(
                batchSize,
                transactionRunnerPort,
                dequeueJobStepsPort,
                finishJobStepPort,
                finishJobPort,
                taskExecutorPort,
                githubStepCollector,
                jenkinsStepCollector,
                deploykoStepCollector
        );
    }

}
