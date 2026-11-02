package com.wultra.wallet.rateclient.configuration;

import com.wultra.wallet.rateclient.RateFetchClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Clock;
import java.time.ZoneId;

@AutoConfiguration
@EnableConfigurationProperties(RateFetchClientProperties.class)
public class RateFetchClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(RateFetchClient.class)
    RateFetchClient rateFetchClient(RateFetchClientProperties properties, ObjectProvider<Clock> clocks) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeout());
        factory.setReadTimeout(properties.getRequestTimeout());
        Clock clock = clocks.getIfAvailable(() -> Clock.system(ZoneId.of("Europe/Prague")));
        return new RateFetchClient(new RestTemplate(factory), properties.getUrl(), clock);
    }

}
