package com.wultra.wallet.rateclient.configuration;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

@Data
@Validated
@ConfigurationProperties("restclient.rates")
public class RateFetchClientProperties {
    @NotNull
    private URI url = URI.create("https://www.cnb.cz/en/financial-markets/foreign-exchange-market/central-bank-exchange-rate-fixing/central-bank-exchange-rate-fixing/daily.txt");
    @NotNull
    private Duration connectTimeout = Duration.ofSeconds(2);
    @NotNull
    private Duration requestTimeout = Duration.ofSeconds(4);

    @AssertTrue(message = "Rate timeouts must be positive and URL must use HTTP(S)")
    public boolean isValid() {
        return url != null && ("https".equals(url.getScheme()) || "http".equals(url.getScheme()))
                && connectTimeout != null && connectTimeout.isPositive()
                && requestTimeout != null && requestTimeout.isPositive();
    }
}
