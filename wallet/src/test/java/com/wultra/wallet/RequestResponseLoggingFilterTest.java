package com.wultra.wallet;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.wultra.wallet.filter.RequestResponseLoggingFilter;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import jakarta.servlet.ServletException;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestResponseLoggingFilterTest {

    @Test
    void logsJsonWithoutChangingBodiesAndRedactsCredentials() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(RequestResponseLoggingFilter.class);
        Level previousLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
        try {
            RequestResponseLoggingFilter filter = new RequestResponseLoggingFilter();
            String body = "{\"currency\":\"EUR\",\"amount\":10}";
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/wallet/deposits");
            request.setContentType("application/json");
            request.setContent(body.getBytes(StandardCharsets.UTF_8));
            request.addHeader("Authorization", "Basic secret");
            request.addHeader("Cookie", "JSESSIONID=secret-session");
            request.addHeader("X-Debug", "visible-debug-value");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, (incoming, outgoing) -> {
                assertThat(new String(incoming.getInputStream().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(body);
                outgoing.setContentType("application/json");
                ((jakarta.servlet.http.HttpServletResponse) outgoing).addHeader("Set-Cookie", "JSESSIONID=secret-cookie; Path=/; HttpOnly");
                outgoing.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
            });
            assertThat(response.getContentAsString()).isEqualTo(body);
            String logs = appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .reduce("", (left, right) -> left + "\n" + right);
            assertThat(logs).contains("REQUEST method=POST", "RESPONSE method=POST", "status=200", body, "[REDACTED]", "visible-debug-value")
                    .doesNotContain("Basic secret", "secret-session", "secret-cookie");

            appender.list.clear();
            MockHttpServletRequest login = new MockHttpServletRequest("POST", "/api/auth/login");
            login.setContentType("application/x-www-form-urlencoded");
            login.setContent("username=alice&password=hidden-password".getBytes(StandardCharsets.UTF_8));
            filter.doFilter(login, new MockHttpServletResponse(), (incoming, outgoing) -> {
                incoming.getInputStream().readAllBytes();
                outgoing.setContentType("application/json");
                outgoing.getWriter().write("{\"token\":\"hidden-token\"}");
            });
            assertThat(appender.list).hasSize(2).allSatisfy(event ->
                    assertThat(event.getFormattedMessage()).contains("body=[REDACTED]")
                            .doesNotContain("hidden-password", "hidden-token"));

            MockHttpServletResponse failedResponse = new MockHttpServletResponse();
            assertThatThrownBy(() -> filter.doFilter(
                    new MockHttpServletRequest("GET", "/api/wallet/balances"), failedResponse,
                    (incoming, outgoing) -> {
                        outgoing.getOutputStream().write("partial response".getBytes(StandardCharsets.UTF_8));
                        throw new ServletException("downstream failure");
                    })).isInstanceOf(ServletException.class);
            assertThat(failedResponse.getContentAsString()).isEqualTo("partial response");
            assertThat(appender.list).anySatisfy(event ->
                    assertThat(event.getFormattedMessage()).contains("body=partial response"));
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
            appender.stop();
        }
    }
}
