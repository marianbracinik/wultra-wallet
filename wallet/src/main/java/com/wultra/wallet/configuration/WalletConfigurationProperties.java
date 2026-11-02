package com.wultra.wallet.configuration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Data
@Validated
@ConfigurationProperties("wallet")
public class WalletConfigurationProperties {

    @Valid
    @NotNull
    private RatesConfig rates = new RatesConfig();

    @Valid
    @NotNull
    private UsersConfig users = new UsersConfig();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RatesConfig {

        @NotNull
        private Duration refreshInterval;

        /**
         * Checks whether the refresh interval is configured and positive.
         *
         * @return true if the refresh interval is valid
         */
        @AssertTrue(message = "Refresh interval must be positive")
        public boolean isValid() {
            return positive(refreshInterval);
        }

        private boolean positive(Duration duration) {
            return duration != null && !duration.isNegative() && !duration.isZero();
        }
    }

    @Data
    public static class UsersConfig {
        @NotBlank
        private String alicePassword;

        @NotBlank
        private String bobPassword;

        /**
         * Returns a description that hides configured passwords.
         *
         * @return redacted configuration description
         */
        @Override
        public String toString() { return "UsersConfig[--hidden--]"; }
    }
}
