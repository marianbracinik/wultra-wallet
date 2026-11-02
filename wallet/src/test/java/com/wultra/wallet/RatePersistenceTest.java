package com.wultra.wallet;

import com.wultra.wallet.data.Rate;
import com.wultra.wallet.exception.RateException;
import com.wultra.wallet.service.DbServiceService;
import com.wultra.wallet.domain.WalletEntity;
import com.wultra.wallet.util.RateSnapshotParser;
import com.wultra.wallet.data.RateSnapshot;
import com.wultra.wallet.repository.ExchangeRateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
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
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import static org.assertj.core.api.Assertions.*;

class RatePersistenceTest {
    @TempDir Path directory;

    @Test
    void loadsSnapshotAndRatesWithOneQuery() {
        database().withPropertyValues("spring.jpa.properties.hibernate.generate_statistics=true").run(context -> {
            DbServiceService service = context.getBean(DbServiceService.class);
            assertThat(service.loadRateSnapshot()).isEmpty();
            RateSnapshot snapshot = RateSnapshotParser.parse(WalletApiTest.CNB, Instant.parse("2026-11-02T10:00:00Z"), LocalDate.of(2026, 11, 2));
            service.saveRateSnapshot(snapshot);
            Statistics statistics = context.getBean(EntityManagerFactory.class).unwrap(SessionFactory.class).getStatistics();
            statistics.clear();
            RateSnapshot loaded = service.loadRateSnapshot().orElseThrow();
            assertThat(loaded.getRates().keySet()).isEqualTo(snapshot.getRates().keySet());
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);

            TransactionTemplate transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            transaction.executeWithoutResult(status -> {
                service.saveRateSnapshot(replacement(snapshot));
                assertThat(service.loadRateSnapshot().orElseThrow().getRates()).doesNotContainKey("JPY");
            });
        });
    }

    @Test
    void existingDatabaseGainsSnapshotForeignKeyWithoutLosingRates() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:file:" + directory.resolve("wallet").toAbsolutePath(), "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("create table rate_snapshot (id integer primary key, rate_date date not null, fetched_at timestamp with time zone not null)");
            statement.execute("create table exchange_rate (currency char(3) primary key, units integer not null, czk_rate decimal(24,12) not null)");
            statement.execute("insert into rate_snapshot values (1, date '2026-10-30', timestamp with time zone '2026-11-02 10:00:00+00')");
            statement.execute("insert into exchange_rate values ('JPY', 100, 15)");
        }
        database().run(context -> {
            assertThat(context).hasNotFailed();
            RateSnapshot loaded = context.getBean(DbServiceService.class).loadRateSnapshot().orElseThrow();
            assertThat(loaded.getRates().get("JPY").getCzkRate()).isEqualByComparingTo("15");
        });
    }

    @Test
    void fileDatabaseSurvivesReopeningAndFailedReplacementRollsBack() throws RateException {
        RateSnapshot snapshot = RateSnapshotParser.parse(WalletApiTest.CNB, Instant.parse("2026-11-02T10:00:00Z"), LocalDate.of(2026, 11, 2));
        ApplicationContextRunner runner = database();
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            DbServiceService repository = context.getBean(DbServiceService.class);
            DbServiceService wallets = context.getBean(DbServiceService.class);
            repository.saveRateSnapshot(snapshot);
            wallets.updateCurrencyBalance("alice", "CZK", new BigDecimal("10"));
            wallets.updateCurrencyBalance("alice", "CZK", new BigDecimal("-2"));

            TransactionTemplate transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
                repository.saveRateSnapshot(replacement(snapshot));
                context.getBean(ExchangeRateRepository.class).flush();
                throw new IllegalStateException("Interrupted replacement");
            })).isInstanceOf(IllegalStateException.class);
        });

        // reopen both the file database and JPA context after the first context closes
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            RateSnapshot loaded = context.getBean(DbServiceService.class).loadRateSnapshot().orElseThrow();
            assertThat(loaded.getRateDate()).isEqualTo(snapshot.getRateDate());
            assertThat(loaded.getFetchedAt()).isEqualTo(snapshot.getFetchedAt());
            assertThat(loaded.getRates()).containsOnlyKeys(snapshot.getRates().keySet());
            assertThat(loaded.getRates().get("JPY").getCzkRate()).isEqualByComparingTo("15");
            assertThat(context.getBean(DbServiceService.class).getBalances("alice").get("CZK")).isEqualByComparingTo("8");
        });
    }

    @Test
    void replacementUpdatesRatesAndRemovesCurrenciesNoLongerPublished() {
        database().run(context -> {
            assertThat(context).hasNotFailed();
            DbServiceService repository = context.getBean(DbServiceService.class);
            RateSnapshot first = RateSnapshotParser.parse(WalletApiTest.CNB, Instant.parse("2026-11-02T10:00:00Z"), LocalDate.of(2026, 11, 2));
            repository.saveRateSnapshot(first);
            RateSnapshot replacement = replacement(first);
            repository.saveRateSnapshot(replacement);
            RateSnapshot loaded = repository.loadRateSnapshot().orElseThrow();
            assertThat(loaded.getRateDate()).isEqualTo(replacement.getRateDate());
            assertThat(loaded.getFetchedAt()).isEqualTo(replacement.getFetchedAt());
            assertThat(loaded.getRates()).doesNotContainKey("JPY");
            assertThat(loaded.getRates().get("USD").getCzkRate()).isEqualByComparingTo("22");
            assertThat(loaded.getRates().get("GBP").getUnits()).isEqualTo(1);
        });
    }

    private RateSnapshot replacement(RateSnapshot original) {
        Map<String, Rate> rates = new HashMap<>(original.getRates());
        rates.remove("JPY");
        rates.put("USD", new Rate(1, new BigDecimal("22")));
        rates.put("GBP", new Rate(1, new BigDecimal("30")));
        return new RateSnapshot(rates, original.getRateDate().plusDays(1), original.getFetchedAt().plusSeconds(3600));
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
    @Import(DbServiceService.class)
    static class PersistenceConfiguration {
    }
}
