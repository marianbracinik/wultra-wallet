package com.wultra.wallet.configuration;

import com.wultra.wallet.error.WalletExceptionHandler;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.MissingCsrfTokenException;
import org.springframework.security.web.csrf.InvalidCsrfTokenException;

@Configuration
public class SecurityConfiguration {

    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    UserDetailsService userDetailsService(WalletConfigurationProperties properties, PasswordEncoder encoder) {
        return new InMemoryUserDetailsManager(
                User.withUsername("alice").password(encoder.encode(properties.getUsers().getAlicePassword())).roles("USER").build(),
                User.withUsername("bob").password(encoder.encode(properties.getUsers().getBobPassword())).roles("USER").build());
    }

    @Bean
    @Order(1)
    SecurityFilterChain metricsSecurity(HttpSecurity http) throws Exception {
        http.securityMatcher("/actuator/prometheus")
                .authorizeHttpRequests(authorize -> authorize.requestMatchers(HttpMethod.GET, "/actuator/prometheus")
                        .authenticated()
                        .anyRequest().denyAll())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(RequestCacheConfigurer::disable)
                .exceptionHandling(errors -> errors.accessDeniedHandler((request, response, exception) ->
                        WalletExceptionHandler.writeToResponse(response, HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Access denied.")))
                .httpBasic(basic -> basic.authenticationEntryPoint((request, response, exception) -> {
                    response.setHeader("WWW-Authenticate", "Basic realm=\"metrics\"");
                    WalletExceptionHandler.writeToResponse(response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required.");
                }));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain security(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/api/auth/csrf",
                                "/api/auth/login",
                                "/actuator/health",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated())
                .requestCache(cache -> cache.disable())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> WalletExceptionHandler.writeToResponse(response,
                                HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required."))
                        .accessDeniedHandler((request, response, exception) -> {
                            String detail = "Access denied.";
                            if (exception instanceof MissingCsrfTokenException) {
                                detail = "CSRF token is missing or the CSRF session has expired.";
                            } else if (exception instanceof InvalidCsrfTokenException) {
                                detail = "CSRF token is invalid for the current session.";
                            }
                            WalletExceptionHandler.writeToResponse(response, HttpStatus.FORBIDDEN, "ACCESS_DENIED", detail);
                        }))
                .formLogin(login -> login.loginProcessingUrl("/api/auth/login")
                        .successHandler((request, response, authentication) -> response.setStatus(204))
                        .failureHandler((request, response, exception) -> WalletExceptionHandler.writeToResponse(response,
                                HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid username or password.")))
                .logout(logout -> logout.logoutUrl("/api/auth/logout")
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)));
        return http.build();
    }

    @Bean
    OpenAPI openAPI() {
        return new OpenAPI().info(new Info()
                        .title("Multicurrency Wallet")
                        .version("1.0")
                        .description("Login accepts form fields username/password. Obtain a CSRF token before login and again after login; "
                                + "send it in the returned header on every POST and DELETE, including logout. Use the session cookie on subsequent requests."))
                .components(new Components().addSecuritySchemes("session", new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.COOKIE).name("JSESSIONID"))
                        .addSecuritySchemes("csrf", new SecurityScheme().type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER).name("X-CSRF-TOKEN")))
                .addSecurityItem(new SecurityRequirement().addList("session").addList("csrf"));
    }
}
