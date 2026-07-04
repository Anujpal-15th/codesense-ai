package com.codesense;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CodeSenseApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeSenseApplication.class, args);
    }
}
