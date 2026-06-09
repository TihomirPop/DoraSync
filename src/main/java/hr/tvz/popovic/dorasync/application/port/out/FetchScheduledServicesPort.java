package hr.tvz.popovic.dorasync.application.port.out;

import hr.tvz.popovic.dorasync.application.domain.model.Service;

import java.util.List;

public interface FetchScheduledServicesPort {

    Result fetchScheduledServices();

    sealed interface Result {

        record Success(List<Service> services) implements Result {
        }

        record Failure() implements Result {
        }
    }

}
