package com.wultra.wallet.service;

import lombok.Value;
import com.wultra.wallet.configuration.RateProperties;
import com.wultra.wallet.error.WalletException;
import com.wultra.wallet.service.RatePersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

@Service
public class RateService {
    private static final Logger log = LoggerFactory.getLogger(RateService.class);
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;
    private final RatePersistenceService repository;
    private final RateProperties properties;
    private final Clock clock;
    private final HttpClient client;
    private RateSnapshot snapshot;
    private Instant nextRefresh = Instant.MIN;
    private boolean fallback;

    public RateService(RatePersistenceService repository, RateProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
        this.client = HttpClient.newBuilder().connectTimeout(properties.getConnectTimeout()).build();
        this.snapshot = repository.load().orElse(null);
    }

    public synchronized Rates current() {
        Instant now = clock.instant();
        if (!now.isBefore(nextRefresh)) {
            nextRefresh = now.plus(properties.getRefreshInterval());
            try {
                log.trace("Refreshing official exchange rates.");
                RateSnapshot fetched = fetch(now);
                if (snapshot != null && fetched.getRateDate().isBefore(snapshot.getRateDate())) {
                    throw new IllegalArgumentException("CNB returned an older snapshot");
                }
                repository.save(fetched);
                snapshot = fetched;
                fallback = false;
                log.debug("Exchange rates refreshed (rateDate: {}, currencies: {}).", fetched.getRateDate(), fetched.getRates().size());
            } catch (IOException | IllegalArgumentException exception) {
                fallback = true;
                log.warn("Exchange rates unavailable; retaining stored snapshot (reason: {}).", exception.getClass().getSimpleName());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                fallback = true;
                log.warn("Exchange rate refresh interrupted; retaining stored snapshot.");
            }
        }
        if (snapshot == null) {
            throw new WalletException(HttpStatus.SERVICE_UNAVAILABLE, "RATES_UNAVAILABLE", "Exchange rates are unavailable.");
        }
        return new Rates(snapshot, fallback);
    }

    private RateSnapshot fetch(Instant now) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(properties.getUrl()).timeout(properties.getRequestTimeout()).GET().build();
        HttpResponse<byte[]> response = client.send(request, info -> new BoundedBodySubscriber());
        if (response.statusCode() != 200) {
            throw new IOException("CNB returned HTTP " + response.statusCode());
        }
        return CnbParser.parse(new String(response.body(), StandardCharsets.UTF_8), now, LocalDate.now(clock));
    }

    @Value
    public static class Rates {
        RateSnapshot snapshot;
        boolean fallback;
    }

    private static final class BoundedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
        private Flow.Subscription subscription;
        private int received;

        @Override
        public CompletionStage<byte[]> getBody() { return delegate.getBody(); }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            delegate.onSubscribe(subscription);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > MAX_RESPONSE_BYTES - received) {
                    subscription.cancel();
                    delegate.onError(new IOException("CNB response too large"));
                    return;
                }
                received += buffer.remaining();
            }
            delegate.onNext(buffers);
        }

        @Override
        public void onError(Throwable error) { delegate.onError(error); }

        @Override
        public void onComplete() { delegate.onComplete(); }
    }
}
