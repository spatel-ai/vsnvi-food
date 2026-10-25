package com.vsnvi.vsnvi_food;

import org.springframework.boot.SpringApplication;

public class TestVsnviFoodApplication {

    public static void main(String[] args) {
        SpringApplication.from(VsnviFoodApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
