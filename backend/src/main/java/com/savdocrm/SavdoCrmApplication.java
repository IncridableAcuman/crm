package com.savdocrm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SavdoCrmApplication {

    public static void main(String[] args) {
        SpringApplication.run(SavdoCrmApplication.class, args);
    }
}
