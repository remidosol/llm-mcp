package com.remidosol.llmmcp.llm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LlmWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LlmWorkerApplication.class, args);
    }
}
