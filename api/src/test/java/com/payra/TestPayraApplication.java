package com.payra;

import org.springframework.boot.SpringApplication;

public class TestPayraApplication {
    public static void main(String[] args) {
        SpringApplication.from(PayraApplication::main).with(TestcontainersConfiguration.class).run(args);
    }
}
