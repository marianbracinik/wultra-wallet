package com.wultra.wallet.util;

import com.wultra.wallet.data.Rate;
import com.wultra.wallet.data.RateSnapshot;
import com.wultra.wallet.exception.RateException;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.*;

import static com.wultra.wallet.data.Rate.RATE_CZK;

/**
 * Parses the CNB daily exchange rate feed into a validated snapshot containing the CZK base rate.
 */
@Slf4j
@UtilityClass
public final class RateSnapshotParser {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM uuuu", Locale.ENGLISH)
            .withResolverStyle(ResolverStyle.STRICT);

    /**
     * Parses and validates the CNB daily feed and adds the CZK base rate.
     *
     * @param text CNB feed content
     * @param fetchedAt retrieval timestamp to attach to the snapshot
     * @param today latest allowed rate date
     * @return validated exchange rate snapshot
     * @throws RateException if the header, date, or any rate row is invalid
     */
    public static RateSnapshot parse(String text, Instant fetchedAt, LocalDate today) throws RateException {
        List<String> lines = text.lines().toList();

        // check rate header
        if (lines.size() < 3 || !lines.get(0).matches("\\d{2} [A-Za-z]{3} \\d{4} #\\d+")
                || !"Country|Currency|Amount|Code|Rate".equals(lines.get(1))) {
            throw new RateException("Invalid CNB rates header");
        }

        // parse rate date
        LocalDate rateDate;
        try {
            rateDate = LocalDate.parse(lines.getFirst().split(" #")[0], DATE_FORMATTER);
        } catch (DateTimeParseException exception) {
            throw new RateException("Invalid CNB rate date.");
        }
        if (rateDate.isAfter(today)) {
            log.info("Invalid CNB rate date. The date ({}) is in the future.", rateDate);
            throw new RateException("Invalid CNB rate date. The date is in the future.");
        }

        // create the rates
        Map<String, Rate> rates = new HashMap<>();
        rates.put("CZK", RATE_CZK);
        for (String line : lines.subList(2, lines.size())) {
            String[] fields = line.split("\\|", -1);
            if (fields.length != 5
                    || !fields[2].matches("[1-9][0-9]{0,6}")
                    || !fields[3].matches("[A-Z]{3}")
                    || !fields[4].matches("[0-9]{1,12}(\\.[0-9]{1,12})?")) {
                log.info("Invalid CNB row ({})", line);
                throw new RateException("Invalid CNB row.");
            }

            // currency
            String currencyCode = fields[3];
            try {
                Currency.getInstance(currencyCode);
            } catch (Exception e) {
                log.info("Invalid CNB row. Currency ({}) is invalid.", currencyCode);
                throw new RateException("Invalid CNB row. Currency is invalid.");
            }

            // units
            int units;
            try {
                units = Integer.parseInt(fields[2]);
            } catch (Exception e) {
                log.info("Invalid CNB row. Units are invalid (currency: {}, units: {}).", currencyCode, fields[2]);
                throw new RateException("Invalid CNB row. Units are invalid.");
            }

            // rate
            BigDecimal rate = new BigDecimal(fields[4]);
            if (rate.signum() <= 0 || rates.putIfAbsent(currencyCode, new Rate(units, rate)) != null) {
                log.info("Invalid CNB row. Invalid or duplicate CNB rate (currency: {}, units: {}).", currencyCode, rate);
                throw new RateException("Invalid CNB row. Invalid or duplicate CNB rate");
            }
        }

        return new RateSnapshot(rates, rateDate, fetchedAt);
    }
}
