package com.nxtgen.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.nxtgen")
@EnableScheduling
public class NxtGenApplication {
    public static void main(String[] args) {
        SpringApplication.run(NxtGenApplication.class, args);
    }
}
