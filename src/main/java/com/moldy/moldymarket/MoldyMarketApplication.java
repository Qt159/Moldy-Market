package com.moldy.moldymarket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MoldyMarketApplication {

    public static void main(String[] args) {
        SpringApplication.run(MoldyMarketApplication.class, args);
    }

}
