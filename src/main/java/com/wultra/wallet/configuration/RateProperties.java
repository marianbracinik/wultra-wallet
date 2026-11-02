package com.wultra.wallet.configuration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.net.URI;
import java.time.Duration;

@Validated
@ConfigurationProperties("wallet.rates")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RateProperties {

    @NotNull
    private URI url;

    @NotNull
    private Duration refreshInterval;

    @NotNull
    private Duration connectTimeout;

    @NotNull
    private Duration requestTimeout;

    @AssertTrue(message = "Rate durations must be positive and URL must use HTTP(S)")
    public boolean isValid() {
        return url != null && ("https".equals(url.getScheme()) || "http".equals(url.getScheme()))
                && positive(refreshInterval) && positive(connectTimeout) && positive(requestTimeout);
    }

    private boolean positive(Duration duration) {
        return duration != null && !duration.isNegative() && !duration.isZero();
    }
}
