package com.wultra.wallet;

import com.wultra.wallet.service.RatePersistenceService;
import com.wultra.wallet.service.WalletPersistenceService;
import com.wultra.wallet.domain.WalletEntity;
import com.wultra.wallet.service.CnbParser;
import com.wultra.wallet.service.RateSnapshot;
import com.wultra.wallet.repository.ExchangeRateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class RatePersistenceTest {
    @TempDir Path directory;

    @Test
    void fileDatabaseSurvivesReopeningAndFailedReplacementRollsBack() {
        RateSnapshot snapshot = CnbParser.parse(WalletApiTest.CNB, Instant.parse("2026-11-02T10:00:00Z"), LocalDate.of(2026, 11, 2));
        ApplicationContextRunner runner = database();
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            RatePersistenceService repository = context.getBean(RatePersistenceService.class);
            WalletPersistenceService wallets = context.getBean(WalletPersistenceService.class);
            repository.save(snapshot);
            wallets.change("alice", "CZK", new BigDecimal("10"));
            wallets.change("alice", "CZK", new BigDecimal("-2"));

            TransactionTemplate transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
                repository.save(replacement(snapshot));
                context.getBean(ExchangeRateRepository.class).flush();
                throw new IllegalStateException("Interrupted replacement");
            })).isInstanceOf(IllegalStateException.class);
        });

        // reopen both the file database and JPA context after the first context closes
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            RateSnapshot loaded = context.getBean(RatePersistenceService.class).load().orElseThrow();
            assertThat(loaded.getRateDate()).isEqualTo(snapshot.getRateDate());
            assertThat(loaded.getFetchedAt()).isEqualTo(snapshot.getFetchedAt());
            assertThat(loaded.getRates()).containsOnlyKeys(snapshot.getRates().keySet());
            assertThat(loaded.getRates().get("JPY").getCzkRate()).isEqualByComparingTo("15");
            assertThat(context.getBean(WalletPersistenceService.class).getBalances("alice").get("CZK")).isEqualByComparingTo("8");
        });
    }

    @Test
    void replacementUpdatesRatesAndRemovesCurrenciesNoLongerPublished() {
        database().run(context -> {
            assertThat(context).hasNotFailed();
            RatePersistenceService repository = context.getBean(RatePersistenceService.class);
            RateSnapshot first = CnbParser.parse(WalletApiTest.CNB, Instant.parse("2026-11-02T10:00:00Z"), LocalDate.of(2026, 11, 2));
            repository.save(first);
            RateSnapshot replacement = replacement(first);
            repository.save(replacement);
            RateSnapshot loaded = repository.load().orElseThrow();
            assertThat(loaded.getRateDate()).isEqualTo(replacement.getRateDate());
            assertThat(loaded.getFetchedAt()).isEqualTo(replacement.getFetchedAt());
            assertThat(loaded.getRates()).doesNotContainKey("JPY");
            assertThat(loaded.getRates().get("USD").getCzkRate()).isEqualByComparingTo("22");
            assertThat(loaded.getRates().get("GBP").getUnits()).isEqualTo(1);
        });
    }

    private RateSnapshot replacement(RateSnapshot original) {
        Map<String, RateSnapshot.Rate> rates = new HashMap<>(original.getRates());
        rates.remove("JPY");
        rates.put("USD", new RateSnapshot.Rate(1, new BigDecimal("22")));
        rates.put("GBP", new RateSnapshot.Rate(1, new BigDecimal("30")));
        return new RateSnapshot(original.getRateDate().plusDays(1), original.getFetchedAt().plusSeconds(3600), rates);
    }

    private ApplicationContextRunner database() {
        return new ApplicationContextRunner().withUserConfiguration(PersistenceConfiguration.class)
                .withPropertyValues("spring.datasource.url=jdbc:h2:file:" + directory.resolve("wallet").toAbsolutePath() + ";WRITE_DELAY=0",
                        "spring.datasource.username=sa", "spring.datasource.password=", "spring.sql.init.mode=always",
                        "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.open-in-view=false");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = WalletEntity.class)
    @Import({RatePersistenceService.class, WalletPersistenceService.class})
    static class PersistenceConfiguration {
    }
}
