package com.wultra.wallet.service;

import com.wultra.wallet.data.RateSnapshot;
import com.wultra.wallet.rateclient.RateFetchClient;
import com.wultra.wallet.configuration.WalletConfigurationProperties;
import com.wultra.wallet.exception.RateException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.Instant;

@Slf4j
@Service
public class RateService {

    private final DbServiceService dbService;
    private final RateFetchClient rateFetchClient;
    private final WalletConfigurationProperties configuration;

    private final Clock clock;
    private Instant nextRefresh = Instant.MIN;

    private RateSnapshot snapshot;

    public RateService(DbServiceService dbService, WalletConfigurationProperties configuration, RateFetchClient rateFetchClient, Clock clock) {
        this.dbService = dbService;
        this.rateFetchClient = rateFetchClient;
        this.configuration = configuration;
        this.clock = clock;
        this.snapshot = dbService.loadRateSnapshot().orElse(null);
    }

    public synchronized RateSnapshot getRateSnapshot() throws RateException {
        Instant now = clock.instant();
        if (!now.isBefore(nextRefresh)) {
            nextRefresh = now.plus(this.configuration.getRates().getRefreshInterval());
            try {

                // fetch
                log.trace("Exchange rates refresh...");
                RateSnapshot newSnapshot = rateFetchClient.fetch().withFreshSnapshot(true);
                if (snapshot != null && newSnapshot.getRateDate().isBefore(snapshot.getRateDate())) {
                    throw new RateException("CNB returned an older snapshot");
                }

                // save
                dbService.saveRateSnapshot(newSnapshot);
                snapshot = newSnapshot;
                log.debug("Exchange rates refreshed (rateDate: {}, currencies: {}).", newSnapshot.getRateDate(), newSnapshot.getRates().size());
            } catch (Exception exception) {
                if (snapshot != null) {
                    snapshot = snapshot.withFreshSnapshot(false);
                }
                log.warn("Exchange rates unavailable; retaining stored snapshot (reason: {}).", exception.getClass().getSimpleName());
            }
        }
        if (snapshot == null) {
            throw new RateException("Exchange rates are unavailable and no stored snapshot exists.");
        }
        return snapshot;
    }

}
