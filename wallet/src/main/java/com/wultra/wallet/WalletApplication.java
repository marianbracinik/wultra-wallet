package com.wultra.wallet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import java.time.Clock;
import java.time.ZoneId;

@SpringBootApplication
@ConfigurationPropertiesScan
public class WalletApplication {
    /**
     * Starts the wallet application.
     *
     * @param args application startup arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(WalletApplication.class, args);
    }

    @Bean
    Clock clock() {
        return Clock.system(ZoneId.of("Europe/Prague"));
    }
}
