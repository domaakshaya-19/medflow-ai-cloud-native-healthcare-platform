package com.medflow.ai;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication public class AiApplication { public static void main(String[] args){SpringApplication.run(AiApplication.class,args);} @org.springframework.context.annotation.Bean java.time.Clock clock(){return java.time.Clock.systemUTC();} }
