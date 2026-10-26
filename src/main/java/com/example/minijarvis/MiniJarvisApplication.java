package com.example.minijarvis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MiniJarvisApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiniJarvisApplication.class, args);
    }
}
