package com.wultra.wallet;

import com.sun.net.httpserver.HttpServer;
import com.wultra.wallet.configuration.WalletConfigurationProperties;
import com.wultra.wallet.data.RateSnapshot;
import com.wultra.wallet.exception.RateException;
import com.wultra.wallet.rateclient.RateFetchClient;
import com.wultra.wallet.service.DbServiceService;
import com.wultra.wallet.service.RateService;
import com.wultra.wallet.util.RateSnapshotParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class RateServiceTest {
    private HttpServer server;
    private final DbServiceService repository = mock(DbServiceService.class);
    private final MutableClock clock = new MutableClock();
    private final AtomicInteger requests = new AtomicInteger();
    private String body = WalletApiTest.CNB;
    private int status = 200;
    private WalletConfigurationProperties properties;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/rates", exchange -> {
            requests.incrementAndGet();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        properties = new WalletConfigurationProperties();
        properties.setRates(new WalletConfigurationProperties.RatesConfig(
                Duration.ofMinutes(5)));
        when(repository.loadRateSnapshot()).thenReturn(Optional.empty());
    }

    @AfterEach
    void stop() { server.stop(0); }

    @Test
    void refreshesPersistsAndFallsBackWithoutOverwritingGoodData() throws RateException {
        RateService service = new RateService(repository, properties, client(), clock);
        RateSnapshot first = service.getRateSnapshot();
        assertThat(first.isFreshSnapshot()).isTrue();
        assertThat(first.getFetchedAt()).isEqualTo(clock.instant());
        verify(repository).saveRateSnapshot(first);
        service.getRateSnapshot();
        assertThat(requests.get()).isEqualTo(1);

        clock.advance();
        body = "not a valid rate snapshot";
        RateSnapshot fallback = service.getRateSnapshot();
        assertThat(fallback.isFreshSnapshot()).isFalse();
        assertThat(fallback).isEqualTo(first.withFreshSnapshot(false));
        assertThat(first.isFreshSnapshot()).isTrue();
        verify(repository, times(1)).saveRateSnapshot(any());
        service.getRateSnapshot();
        assertThat(requests.get()).isEqualTo(2);
        assertThat(service.getRateSnapshot().isFreshSnapshot()).isFalse();

        when(repository.loadRateSnapshot()).thenReturn(Optional.of(first));
        status = 503;
        RateService restarted = new RateService(repository, properties, client(), clock);
        assertThat(restarted.getRateSnapshot()).isEqualTo(first.withFreshSnapshot(false));
        assertThat(restarted.getRateSnapshot().isFreshSnapshot()).isFalse();

        clock.advance();
        status = 200;
        body = WalletApiTest.CNB;
        assertThat(restarted.getRateSnapshot().isFreshSnapshot()).isTrue();
    }

    @Test
    void rejectsOlderAndInvalidDatedSnapshotsWithoutReplacingStoredRates() throws RateException {
        RateSnapshot stored = RateSnapshotParser.parse(WalletApiTest.CNB, clock.instant(), LocalDate.now(clock));
        when(repository.loadRateSnapshot()).thenReturn(Optional.of(stored));
        RateService service = new RateService(repository, properties, client(), clock);
        for (String date : new String[]{"29 Oct 2026", "31 Feb 2026", "03 Nov 2026"}) {
            body = WalletApiTest.CNB.replace("30 Oct 2026", date);
            RateSnapshot result = service.getRateSnapshot();
            assertThat(result.isFreshSnapshot()).isFalse();
            assertThat(result).isSameAs(stored);
            clock.advance();
        }
        verify(repository, never()).saveRateSnapshot(any());
    }

    @Test
    void failsWithoutAnySnapshot() {
        status = 503;
        RateService service = new RateService(repository, properties, client(), clock);
        assertThatThrownBy(service::getRateSnapshot).isInstanceOfSatisfying(RateException.class, exception -> {
            assertThat(exception.getCode()).isEqualTo("RATES_ERROR");
        });
        verify(repository, never()).saveRateSnapshot(any());
    }

    @Test
    void parserHandlesUnitsAndRejectsBadDatesDuplicatesAndZeroRates() throws RateException {
        RateSnapshot snapshot = RateSnapshotParser.parse(WalletApiTest.CNB, clock.instant(), LocalDate.now(clock));
        assertThat(snapshot.getRates().get("JPY").getUnits()).isEqualTo(100);
        assertThat(snapshot.getRates().get("CZK").getCzkRate()).isEqualByComparingTo("1");
        for (String invalid : new String[]{WalletApiTest.CNB.replace("30 Oct 2026", "30 Oct 2027"),
                WalletApiTest.CNB.replace("25.000", "0.000"),
                WalletApiTest.CNB + "EMU|euro|1|EUR|25.000\n",
                WalletApiTest.CNB.replace("|100|JPY|", "|0|JPY|")}) {
            assertThatThrownBy(() -> RateSnapshotParser.parse(invalid, clock.instant(), LocalDate.now(clock)))
                    .isInstanceOf(RateException.class);
        }
    }

    private RateFetchClient client() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(1));
        factory.setReadTimeout(Duration.ofSeconds(2));
        return new RateFetchClient(new RestTemplate(factory),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/rates"), clock);
    }

    private static class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-11-02T10:00:00Z");
        void advance() { instant = instant.plus(Duration.ofMinutes(5)); }
        @Override public ZoneId getZone() { return ZoneId.of("Europe/Prague"); }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant, zone); }
        @Override public Instant instant() { return instant; }
    }
}
