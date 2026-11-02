package com.wultra.wallet;

import com.wultra.wallet.data.Rate;
import com.wultra.wallet.data.RateSnapshot;
import com.wultra.wallet.exception.*;
import com.wultra.wallet.repository.BalanceRepository;
import com.wultra.wallet.service.DbServiceService;
import com.wultra.wallet.service.RateService;
import com.wultra.wallet.service.WalletService;
import com.wultra.wallet.util.RateSnapshotParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:api;DB_CLOSE_DELAY=-1",
        "wallet.users.alice-password=alice-test-password", "wallet.users.bob-password=bob-test-password",
        "server.servlet.session.cookie.secure=false"})
@AutoConfigureMockMvc
@AutoConfigureMetrics
class WalletApiTest {
    @Autowired MockMvc mvc;
    @Autowired BalanceRepository balanceRepository;
    @Autowired WalletService wallet;
    @Autowired
    DbServiceService repository;
    @Autowired ObjectMapper mapper;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean RateService rates;

    static final String CNB = """
            30 Oct 2026 #209
            Country|Currency|Amount|Code|Rate
            EMU|euro|1|EUR|25.000
            USA|dollar|1|USD|20.000
            Japan|yen|100|JPY|15.000
            Hungary|forint|100|HUF|6.000
            IMF|SDR|1|XDR|30.000
            """;

    @BeforeEach
    void setup() throws RateException {
        balanceRepository.deleteAllInBatch();
        RateSnapshot snapshot = RateSnapshotParser.parse(CNB, Instant.parse("2026-11-02T10:00:00Z"), LocalDate.of(2026, 11, 2));
        when(rates.getRateSnapshot()).thenReturn(snapshot.withFreshSnapshot(true));
    }

    @Test
    void clearAllDeletesOnlyAuthenticatedUsersBalances() throws Exception {
        wallet.createDeposit("alice", "CZK", new BigDecimal("10"));
        wallet.createDeposit("alice", "EUR", new BigDecimal("5"));
        wallet.createDeposit("bob", "CZK", new BigDecimal("20"));

        mvc.perform(delete("/api/wallet/balances").with(user("alice")).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(repository.getBalances("alice")).isEmpty();
        assertThat(repository.getBalances("bob").get("CZK")).isEqualByComparingTo("20");
        mvc.perform(delete("/api/wallet/balances").with(user("alice")).with(csrf()))
                .andExpect(status().isNoContent());
        wallet.createDeposit("alice", "CZK", BigDecimal.ONE);
        assertThat(repository.getBalances("alice").get("CZK")).isEqualByComparingTo("1");
    }

    @Test
    void clearAllRequiresAuthenticationAndCsrf() throws Exception {
        wallet.createDeposit("alice", "CZK", BigDecimal.TEN);
        mvc.perform(delete("/api/wallet/balances").with(csrf()))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/wallet/balances").with(user("alice")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("CSRF token is missing or the CSRF session has expired."));
        assertThat(repository.getBalances("alice").get("CZK")).isEqualByComparingTo("10");
    }

    @Test
    void loginRotatesSessionAndLogoutRevokesAccess() throws Exception {
        MvcResult initial = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) initial.getRequest().getSession(false);
        String originalId = session.getId();
        String loginToken = mapper.readTree(initial.getResponse().getContentAsString()).get("token").asText();
        mvc.perform(post("/api/auth/login").session(session).with(request -> {
                    request.setRequestedSessionId(session.getId());
                    request.setRequestedSessionIdValid(true);
                    return request;
                }).header("X-CSRF-TOKEN", loginToken)
                        .param("username", "alice").param("password", "alice-test-password"))
                .andExpect(status().isNoContent());
        assertThat(session.getId()).isNotEqualTo(originalId);
        mvc.perform(get("/api/auth/me").session(session)).andExpect(jsonPath("$.username").value("alice"));
        mvc.perform(post("/api/auth/logout").session(session).header("X-CSRF-TOKEN", loginToken))
                .andExpect(status().isForbidden());
        MvcResult refreshed = mvc.perform(get("/api/auth/csrf").session(session)).andReturn();
        String logoutToken = mapper.readTree(refreshed.getResponse().getContentAsString()).get("token").asText();
        mvc.perform(delete("/api/wallet/balances").session(session).header("X-CSRF-TOKEN", loginToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("CSRF token is invalid for the current session."));
        mvc.perform(delete("/api/wallet/balances").session(session).header("X-CSRF-TOKEN", logoutToken))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/logout").session(session).header("X-CSRF-TOKEN", logoutToken)).andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/wallet/balances")).andExpect(status().isUnauthorized());
    }

    @Test
    void credentialsCsrfAndMonitoringAreProtected() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf()).param("username", "alice").param("password", "wrong"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        mvc.perform(post("/api/wallet/deposits").with(user("alice")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currency\":\"CZK\",\"amount\":10}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/prometheus").with(httpBasic("alice", "alice-test-password"))).andExpect(status().isOk());
    }

    @Test
    @WithMockUser("alice")
    void workflowUsesCrossRatesAndKeepsWalletsPrivate() throws Exception {
        postMoney("deposits", "USD", "10");
        postMoney("deposits", "EUR", "5");
        postMoney("withdrawals", "USD", "3");
        mvc.perform(get("/api/wallet/total").param("currency", "USD"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(13.25))
                .andExpect(jsonPath("$.rateDate").value("2026-10-30"))
                .andExpect(jsonPath("$.rateExchangeUsed").value(true))
                .andExpect(jsonPath("$.freshRateSnapshot").value(true));
        mvc.perform(get("/api/wallet/total").param("currency", "EUR"))
                .andExpect(jsonPath("$.amount").value(10.60));
        mvc.perform(get("/api/wallet/balances").with(user("bob")))
                .andExpect(status().isOk()).andExpect(content().json("{}"));
        mvc.perform(post("/api/wallet/deposits").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currency\":\"CZK\",\"amount\":10,\"userId\":\"bob\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @WithMockUser("alice")
    void validatesAmountsCurrenciesAndFunds() throws Exception {
        for (String amount : List.of("0", "-1", "0.001", "1e26")) {
            mvc.perform(post("/api/wallet/deposits").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"currency\":\"EUR\",\"amount\":" + amount + "}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_AMOUNT"));
        }
        mvc.perform(post("/api/wallet/deposits").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currency\":\"XXX\",\"amount\":1}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNSUPPORTED_CURRENCY"));
        mvc.perform(post("/api/wallet/withdrawals").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currency\":\"CZK\",\"amount\":1}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
        mvc.perform(post("/api/wallet/deposits").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @WithMockUser("alice")
    void totalWithoutConversionOmitsRateMetadata() throws Exception {
        wallet.createDeposit("alice", "CZK", BigDecimal.TEN);
        mvc.perform(get("/api/wallet/total").param("currency", "CZK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("CZK"))
                .andExpect(jsonPath("$.amount").value(10))
                .andExpect(jsonPath("$.rateExchangeUsed").value(false))
                .andExpect(jsonPath("$.rateDate").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.ratesFetchedAt").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.freshRateSnapshot").doesNotHaveJsonPath());
    }

    @Test
    void emptyWalletTotalDoesNotRequireRates() throws WalletException {
        when(rates.getRateSnapshot()).thenThrow(new RateException("Rates unavailable"));
        assertThat(wallet.getTotal("alice", "CZK").getAmount()).isEqualByComparingTo("0");
        verify(rates, never()).getRateSnapshot();
    }

    @Test
    void handlesUnitsRoundingAndUnavailableRates() throws WalletException {
        wallet.createDeposit("alice", "JPY", new BigDecimal("100"));
        assertThat(wallet.getTotal("alice", "CZK").getAmount()).isEqualByComparingTo("15");
        wallet.createDeposit("bob", "CZK", new BigDecimal("0.10"));
        assertThat(wallet.getTotal("bob", "USD").getAmount()).isEqualByComparingTo("0.00");
        wallet.createDeposit("bob", "CZK", new BigDecimal("0.20"));
        assertThat(wallet.getTotal("bob", "USD").getAmount()).isEqualByComparingTo("0.02");
        when(rates.getRateSnapshot()).thenThrow(new RateException("Rates unavailable"));
        wallet.withdraw("alice", "JPY", new BigDecimal("1"));
        assertThat(wallet.getTotal("alice", "JPY").getAmount()).isEqualByComparingTo("99");
        assertThatThrownBy(() -> wallet.getTotal("alice", "EUR")).isInstanceOf(WalletException.class);
        wallet.createDeposit("bob", "CZK", BigDecimal.ONE);
        assertThat(wallet.getTotal("bob", "CZK").getAmount()).isEqualByComparingTo("1.30");
    }

    @Test
    void simultaneousWithdrawalsCannotOverdraw() throws Exception {
        wallet.createDeposit("alice", "CZK", new BigDecimal("100"));
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int index = 0; index < 20; index++) {
                results.add(executor.submit(() -> {
                    start.await();
                    try {
                        wallet.withdraw("alice", "CZK", new BigDecimal("10"));
                        return true;
                    } catch (WalletException exception) {
                        assertThat(exception.getCode()).isEqualTo("INSUFFICIENT_FUNDS");
                        return false;
                    }
                }));
            }
            start.countDown();
            int successful = 0;
            for (Future<Boolean> result : results) {
                if (result.get(10, TimeUnit.SECONDS)) { successful++; }
            }
            assertThat(successful).isEqualTo(10);
        }
        assertThat(repository.getBalances("alice").get("CZK")).isEqualByComparingTo("0");
    }

    @Test
    void concreteExceptionsIdentifyAmountAndWalletFailures() throws WalletException {
        assertThatThrownBy(() -> wallet.createDeposit("alice", "CZK", BigDecimal.ZERO))
                .isInstanceOfSatisfying(AmountException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("INVALID_AMOUNT"));
        assertThatThrownBy(() -> repository.updateCurrencyBalance("missing-owner", "CZK", BigDecimal.ONE))
                .isInstanceOf(WalletNotFoundException.class);
        assertThatThrownBy(() -> wallet.withdraw("alice", "CZK", BigDecimal.ONE))
                .isInstanceOfSatisfying(InsufficientFundsException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("INSUFFICIENT_FUNDS"));

        BigDecimal maximumDeposit = new BigDecimal("99999999999999999999999999");
        wallet.createDeposit("alice", "CZK", maximumDeposit);
        assertThatThrownBy(() -> wallet.createDeposit("alice", "CZK", BigDecimal.ONE))
                .isInstanceOfSatisfying(AmountException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("INVALID_AMOUNT"));
        assertThat(repository.getBalances("alice").get("CZK")).isEqualByComparingTo(maximumDeposit);
    }

    @Test
    void rateExceptionIdentifiesTheHoldingWithoutARate() throws WalletException {
        wallet.createDeposit("alice", "JPY", new BigDecimal("100"));
        RateSnapshot original = rates.getRateSnapshot();
        Map<String, Rate> available = new HashMap<>(original.getRates());
        available.remove("JPY");
        when(rates.getRateSnapshot()).thenReturn(
                new RateSnapshot(available, original.getRateDate(), original.getFetchedAt(), true));

        assertThatThrownBy(() -> wallet.getTotal("alice", "CZK"))
                .isInstanceOfSatisfying(RateException.class, exception -> {
                    assertThat(exception.getMessage()).contains("JPY");
                    assertThat(exception.getCode()).isEqualTo("RATES_ERROR");
                });
    }

    @Test
    void checkedWalletFailureRollsBackEarlierChangesInTheSameTransaction() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            try {
                repository.updateCurrencyBalance("alice", "CZK", new BigDecimal("10"));
                repository.updateCurrencyBalance("alice", "CZK", new BigDecimal("-11"));
            } catch (WalletException exception) {
                assertThat(exception).isInstanceOf(InsufficientFundsException.class);
            }
        })).isInstanceOf(UnexpectedRollbackException.class);
        assertThat(repository.getBalances("alice")).isEmpty();
    }

    @Test
    void simultaneousFirstDepositsDoNotLoseMoney() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
            List<Future<?>> results = new ArrayList<>();
            for (int index = 0; index < 20; index++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return wallet.createDeposit("alice", "CZK", BigDecimal.ONE);
                }));
            }
            start.countDown();
            for (Future<?> result : results) { result.get(10, TimeUnit.SECONDS); }
        }
        assertThat(repository.getBalances("alice").get("CZK")).isEqualByComparingTo("20");
    }

    @Test
    void openApiDescribesWalletAndLogin() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/wallet/deposits'].post").exists())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post").exists())
                .andExpect(jsonPath("$.components.securitySchemes.session.name").value("JSESSIONID"));
    }

    private void postMoney(String operation, String currency, String amount) throws Exception {
        mvc.perform(post("/api/wallet/" + operation).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currency\":\"" + currency + "\",\"amount\":" + amount + "}"))
                .andExpect(status().isOk());
    }
}
