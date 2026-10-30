package com.medflow.lab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class LabServiceApplication {
    public static void main(String[] args) { SpringApplication.run(LabServiceApplication.class, args); }
}
