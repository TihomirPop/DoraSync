package hr.tvz.popovic.dorasync.application.port.out;

public interface TaskExecutorPort {

    void execute(Runnable task);

}
