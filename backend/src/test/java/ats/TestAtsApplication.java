package ats;

import org.springframework.boot.SpringApplication;

public class TestAtsApplication {

    public static void main(String[] args) {
        SpringApplication.from(AtsApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
