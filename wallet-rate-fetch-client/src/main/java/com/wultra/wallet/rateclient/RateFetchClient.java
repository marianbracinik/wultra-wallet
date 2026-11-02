package com.wultra.wallet.rateclient;

import com.wultra.wallet.data.RateSnapshot;
import com.wultra.wallet.exception.RateException;
import com.wultra.wallet.util.RateSnapshotParser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;

@RequiredArgsConstructor
public class RateFetchClient {

    private final RestTemplate restTemplate;
    private final URI url;
    private final Clock clock;

    /**
     * Fetches and validates the configured CNB exchange rate feed.
     *
     * @return parsed snapshot with the retrieval timestamp
     * @throws RateException if retrieval fails, the response is empty, or the feed is invalid
     */
    public RateSnapshot fetch() throws RateException {
        try {
            String body = restTemplate.getForObject(url, String.class);
            if (body == null) {
                throw new RateException("CNB returned an empty response.");
            }
            return RateSnapshotParser.parse(body, clock.instant(), LocalDate.now(clock));
        } catch (RestClientException e) {
            throw new RateException("Could not retrieve CNB exchange rates.", e);
        }
    }
}
