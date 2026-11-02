package com.wultra.wallet.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Logs HTTP exchanges at DEBUG level, hiding authentication bodies and sensitive headers.
 * Request content is cached as it is consumed, without reading the input stream in advance.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestResponseLoggingFilter extends OncePerRequestFilter {

    private static final int BODY_LIMIT = 16 * 1024;
    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "authorization", "proxy-authorization", "cookie", "set-cookie", "x-csrf-token", "x-xsrf-token");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!log.isDebugEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        ContentCachingRequestWrapper cachedRequest = new ContentCachingRequestWrapper(request, BODY_LIMIT);
        ContentCachingResponseWrapper cachedResponse = new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(cachedRequest, cachedResponse);
        } finally {
            try {
                Map<String, List<String>> requestHeaders = new LinkedHashMap<>();
                for (String name : Collections.list(cachedRequest.getHeaderNames())) {
                    requestHeaders.put(name, headerValues(name, Collections.list(cachedRequest.getHeaders(name))));
                }
                Map<String, List<String>> responseHeaders = new LinkedHashMap<>();
                for (String name : cachedResponse.getHeaderNames()) {
                    responseHeaders.put(name, headerValues(name, List.copyOf(cachedResponse.getHeaders(name))));
                }
                String path = request.getRequestURI();
                boolean authentication = path.startsWith(request.getContextPath() + "/api/auth/");
                log.debug("REQUEST method={} path={} headers={} body={}", request.getMethod(), singleLine(path),
                        requestHeaders, body(cachedRequest.getContentAsByteArray(), authentication));
                log.debug("RESPONSE method={} path={} status={} headers={} body={}", request.getMethod(), singleLine(path),
                        cachedResponse.getStatus(), responseHeaders,
                        body(cachedResponse.getContentAsByteArray(), authentication));
            } finally {
                // The caching wrapper must forward the body even when logging fails.
                cachedResponse.copyBodyToResponse();
            }
        }
    }

    private List<String> headerValues(String name, List<String> values) {
        if (SENSITIVE_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
            return List.of("[REDACTED]");
        }
        return values.stream().map(RequestResponseLoggingFilter::singleLine).toList();
    }

    private String body(byte[] content, boolean authentication) {
        return authentication ? "[REDACTED]" : singleLine(new String(content, StandardCharsets.UTF_8));
    }

    private static String singleLine(String value) {
        return value.replace("\r", "\\r").replace("\n", "\\n");
    }
}
