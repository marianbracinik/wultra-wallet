package com.wultra.wallet.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CnbParser {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM uuuu", Locale.ENGLISH)
            .withResolverStyle(ResolverStyle.STRICT);

    private CnbParser() { }

    public static RateSnapshot parse(String text, Instant fetchedAt, LocalDate today) {
        List<String> lines = text.lines().toList();
        if (lines.size() < 3 || !lines.get(0).matches("\\d{2} [A-Za-z]{3} \\d{4} #\\d+")
                || !"Country|Currency|Amount|Code|Rate".equals(lines.get(1))) {
            throw new IllegalArgumentException("Invalid CNB header");
        }
        LocalDate date = LocalDate.parse(lines.get(0).split(" #")[0], DATE);
        if (date.isAfter(today)) {
            throw new IllegalArgumentException("Future CNB rate date");
        }

        // validate the complete snapshot before replacing cached rates
        Map<String, RateSnapshot.Rate> rates = new HashMap<>();
        rates.put("CZK", new RateSnapshot.Rate(1, BigDecimal.ONE));
        for (String line : lines.subList(2, lines.size())) {
            String[] fields = line.split("\\|", -1);
            if (fields.length != 5 || !fields[2].matches("[1-9][0-9]{0,6}")
                    || !fields[3].matches("[A-Z]{3}") || !fields[4].matches("[0-9]{1,12}(\\.[0-9]{1,12})?")) {
                throw new IllegalArgumentException("Invalid CNB row");
            }
            Currency.getInstance(fields[3]);
            int units = Integer.parseInt(fields[2]);
            BigDecimal rate = new BigDecimal(fields[4]);
            if (rate.signum() <= 0 || rates.putIfAbsent(fields[3], new RateSnapshot.Rate(units, rate)) != null) {
                throw new IllegalArgumentException("Invalid or duplicate CNB rate");
            }
        }
        return new RateSnapshot(date, fetchedAt, rates);
    }
}
