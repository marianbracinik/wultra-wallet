package com.wultra.wallet.service;

import com.wultra.wallet.repository.ExchangeRateRepository;
import com.wultra.wallet.repository.RateRepository;
import com.wultra.wallet.domain.ExchangeRateEntity;
import com.wultra.wallet.domain.RateSnapshotEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RatePersistenceService {

    private final RateRepository snapshots;
    private final ExchangeRateRepository exchangeRates;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Optional<RateSnapshot> load() {
        RateSnapshotEntity snapshot = snapshots.findById(1).orElse(null);
        if (snapshot == null) {
            return Optional.empty();
        }
        Map<String, RateSnapshot.Rate> rates = new HashMap<>();
        for (ExchangeRateEntity rate : exchangeRates.findAll()) {
            rates.put(rate.getCurrency(), new RateSnapshot.Rate(rate.getUnits(), rate.getCzkRate()));
        }
        return Optional.of(new RateSnapshot(snapshot.getRateDate(), snapshot.getFetchedAt(), rates));
    }

    @Transactional
    public void save(RateSnapshot snapshot) {
        Map<String, ExchangeRateEntity> storedRates = new HashMap<>();
        for (ExchangeRateEntity rate : exchangeRates.findAll()) {
            storedRates.put(rate.getCurrency(), rate);
        }

        // replace the full snapshot in one transaction, including removed currencies
        for (Map.Entry<String, RateSnapshot.Rate> entry : snapshot.getRates().entrySet()) {
            ExchangeRateEntity stored = storedRates.remove(entry.getKey());
            if (stored == null) {
                exchangeRates.save(new ExchangeRateEntity(entry.getKey(), entry.getValue().getUnits(), entry.getValue().getCzkRate()));
            } else {
                stored.setUnits(entry.getValue().getUnits());
                stored.setCzkRate(entry.getValue().getCzkRate());
            }
        }
        exchangeRates.deleteAll(storedRates.values());
        snapshots.save(new RateSnapshotEntity(1, snapshot.getRateDate(), snapshot.getFetchedAt()));
    }
}
