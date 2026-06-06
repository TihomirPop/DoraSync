package hr.tvz.popovic.dorasync;

import org.springframework.boot.SpringApplication;

public class TestDoraSyncApplication {

    public static void main(String[] args) {
        SpringApplication.from(DoraSyncApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
