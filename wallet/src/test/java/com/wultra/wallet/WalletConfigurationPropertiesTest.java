package com.wultra.wallet;

import com.wultra.wallet.configuration.WalletConfigurationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class WalletConfigurationPropertiesTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class)
            .withPropertyValues("wallet.rates.refresh-interval=5m",
                    "wallet.users.alice-password=alice-test-password", "wallet.users.bob-password=bob-test-password");

    @Test
    void bindsExistingKeysAndHidesNestedPasswords() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            WalletConfigurationProperties properties = context.getBean(WalletConfigurationProperties.class);
            assertThat(properties.getRates().getRefreshInterval()).isEqualTo(Duration.ofMinutes(5));
            assertThat(properties.getUsers().getAlicePassword()).isEqualTo("alice-test-password");
            assertThat(properties.toString()).doesNotContain("alice-test-password", "bob-test-password");
        });
    }

    @Test
    void rejectsInvalidNestedConfiguration() {
        for (String invalidProperty : new String[]{"wallet.rates.refresh-interval=0s", "wallet.users.bob-password="}) {
            runner.withPropertyValues(invalidProperty).run(context -> assertThat(context).hasFailed());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(WalletConfigurationProperties.class)
    static class PropertiesConfiguration {
    }
}
