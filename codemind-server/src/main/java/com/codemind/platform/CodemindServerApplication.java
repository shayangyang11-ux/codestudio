package com.codemind.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.codemind.platform")
@EnableJpaRepositories(basePackages = "com.codemind.platform")
@EntityScan(basePackages = "com.codemind.platform")
public class CodemindServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodemindServerApplication.class, args);
    }
}