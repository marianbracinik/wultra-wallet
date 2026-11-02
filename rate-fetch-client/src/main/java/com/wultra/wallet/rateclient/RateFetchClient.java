package com.wultra.wallet.rateclient;

import com.wultra.wallet.data.RateSnapshot;
import com.wultra.wallet.exception.RateException;
import com.wultra.wallet.util.CnbRateParser;
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

    public RateSnapshot fetch() throws RateException {
        try {
            String body = restTemplate.getForObject(url, String.class);
            if (body == null) {
                throw new RateException("CNB returned an empty response.");
            }
            return CnbRateParser.parse(body, clock.instant(), LocalDate.now(clock));
        } catch (RestClientException exception) {
            throw new RateException("Could not retrieve CNB exchange rates.");
        }
    }
}
