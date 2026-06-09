package hr.tvz.popovic.dorasync;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class DoraSyncApplication {

    public static void main(String[] args) {
        SpringApplication.run(DoraSyncApplication.class, args);
    }

}
