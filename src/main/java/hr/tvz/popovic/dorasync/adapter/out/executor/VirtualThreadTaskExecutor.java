package hr.tvz.popovic.dorasync.adapter.out.executor;

import hr.tvz.popovic.dorasync.application.port.out.TaskExecutorPort;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class VirtualThreadTaskExecutor implements TaskExecutorPort, AutoCloseable {

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @Override
    public void execute(Runnable task) {
        executor.execute(task);
    }

    @Override
    public void close() {
        executor.close();
    }
}
