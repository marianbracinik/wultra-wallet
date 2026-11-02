package com.wultra.wallet;

import com.sun.net.httpserver.HttpServer;
import com.wultra.wallet.configuration.RateProperties;
import com.wultra.wallet.error.WalletException;
import com.wultra.wallet.service.RatePersistenceService;
import com.wultra.wallet.service.CnbParser;
import com.wultra.wallet.service.RateService;
import com.wultra.wallet.service.RateSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RateServiceTest {
    private HttpServer server;
    private final RatePersistenceService repository = mock(RatePersistenceService.class);
    private final MutableClock clock = new MutableClock();
    private final AtomicInteger requests = new AtomicInteger();
    private String body = WalletApiTest.CNB;
    private int status = 200;
    private RateProperties properties;

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
        properties = new RateProperties(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/rates"),
                Duration.ofMinutes(5), Duration.ofSeconds(1), Duration.ofSeconds(2));
        when(repository.load()).thenReturn(Optional.empty());
    }

    @AfterEach
    void stop() { server.stop(0); }

    @Test
    void refreshesPersistsAndFallsBackWithoutOverwritingGoodData() {
        RateService service = new RateService(repository, properties, clock);
        RateService.Rates first = service.current();
        assertThat(first.isFallback()).isFalse();
        verify(repository).save(first.getSnapshot());
        service.current();
        assertThat(requests.get()).isEqualTo(1);

        clock.advance();
        body = "not a valid rate snapshot";
        RateService.Rates fallback = service.current();
        assertThat(fallback.isFallback()).isTrue();
        assertThat(fallback.getSnapshot()).isEqualTo(first.getSnapshot());
        verify(repository, times(1)).save(any());
        service.current();
        assertThat(requests.get()).isEqualTo(2);

        when(repository.load()).thenReturn(Optional.of(first.getSnapshot()));
        status = 503;
        RateService restarted = new RateService(repository, properties, clock);
        assertThat(restarted.current().getSnapshot()).isEqualTo(first.getSnapshot());
        assertThat(restarted.current().isFallback()).isTrue();

        clock.advance();
        status = 200;
        body = WalletApiTest.CNB;
        assertThat(restarted.current().isFallback()).isFalse();
    }

    @Test
    void failsWithoutAnySnapshotAndRejectsOversizedResponses() {
        body = "x".repeat(70_000);
        RateService service = new RateService(repository, properties, clock);
        assertThatThrownBy(service::current).isInstanceOfSatisfying(WalletException.class,
                exception -> assertThat(exception.getCode()).isEqualTo("RATES_UNAVAILABLE"));
        verify(repository, never()).save(any());
    }

    @Test
    void parserHandlesUnitsAndRejectsBadDatesDuplicatesAndZeroRates() {
        RateSnapshot snapshot = CnbParser.parse(WalletApiTest.CNB, clock.instant(), LocalDate.now(clock));
        assertThat(snapshot.getRates().get("JPY").getUnits()).isEqualTo(100);
        assertThat(snapshot.getRates().get("CZK").getCzkRate()).isEqualByComparingTo("1");
        for (String invalid : new String[]{WalletApiTest.CNB.replace("30 Oct 2026", "30 Oct 2027"),
                WalletApiTest.CNB.replace("25.000", "0.000"),
                WalletApiTest.CNB + "EMU|euro|1|EUR|25.000\n",
                WalletApiTest.CNB.replace("|100|JPY|", "|0|JPY|")}) {
            assertThatThrownBy(() -> CnbParser.parse(invalid, clock.instant(), LocalDate.now(clock)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    private static class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-11-02T10:00:00Z");
        void advance() { instant = instant.plus(Duration.ofMinutes(5)); }
        @Override public ZoneId getZone() { return ZoneId.of("Europe/Prague"); }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant, zone); }
        @Override public Instant instant() { return instant; }
    }
}
