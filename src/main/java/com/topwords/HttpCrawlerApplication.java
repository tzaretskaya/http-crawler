package com.topwords;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.annotation.ParametersAreNonnullByDefault;

@SpringBootApplication
@ParametersAreNonnullByDefault
public class HttpCrawlerApplication {

    public static void main(String[] args) {
        SpringApplication.run(HttpCrawlerApplication.class, args);
    }
}
