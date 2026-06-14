package com.nuuly.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class InventoryApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(InventoryApiApplication.class, args);
    }
}
