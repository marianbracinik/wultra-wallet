package com.wultra.wallet.rateclient;

import com.wultra.wallet.data.RateSnapshot;
import com.wultra.wallet.exception.RateException;
import com.wultra.wallet.rateclient.configuration.RateFetchClientAutoConfiguration;
import com.wultra.wallet.rateclient.configuration.RateFetchClientProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class RateFetchClientTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RateFetchClientAutoConfiguration.class));

    @Test
    void autoConfiguresClientAndAllowsReplacement() {
        runner.withPropertyValues("restclient.rates.url=https://example.com/rates", "restclient.rates.request-timeout=3s")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(RateFetchClient.class);
                    assertThat(context.getBean(RateFetchClientProperties.class).getUrl()).isEqualTo(URI.create("https://example.com/rates"));
                    assertThat(context.getBean(RateFetchClientProperties.class).getRequestTimeout()).hasSeconds(3);
                });
        RateFetchClient custom = mock(RateFetchClient.class);
        runner.withBean(RateFetchClient.class, () -> custom)
                .run(context -> assertThat(context.getBean(RateFetchClient.class)).isSameAs(custom));
    }

    @Test
    void rejectsInvalidClientConfiguration() {
        for (String property : new String[]{"restclient.rates.url=file:/rates", "restclient.rates.connect-timeout=0s", "restclient.rates.request-timeout=-1s"}) {
            runner.withPropertyValues(property).run(context -> assertThat(context).hasFailed());
        }
    }

    @Test
    void fetchesAndParsesRatesAndReportsHttpAndEmptyResponses() throws RateException {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        Clock clock = Clock.fixed(Instant.parse("2026-11-02T10:00:00Z"), ZoneOffset.UTC);
        RateFetchClient client = new RateFetchClient(template, URI.create("https://example.com/rates"), clock);
        server.expect(requestTo("https://example.com/rates")).andRespond(withSuccess("""
                30 Oct 2026 #209
                Country|Currency|Amount|Code|Rate
                Japan|yen|100|JPY|15.000
                """, MediaType.TEXT_PLAIN));
        RateSnapshot snapshot = client.fetch();
        assertThat(snapshot.getRates().get("JPY").getUnits()).isEqualTo(100);
        assertThat(snapshot.getFetchedAt()).isEqualTo(clock.instant());
        server.verify();

        server.reset();
        server.expect(requestTo("https://example.com/rates")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThatThrownBy(client::fetch).isInstanceOf(RateException.class);
        server.verify();

        server.reset();
        server.expect(requestTo("https://example.com/rates")).andRespond(withNoContent());
        assertThatThrownBy(client::fetch).isInstanceOf(RateException.class);
        server.verify();
    }
}
