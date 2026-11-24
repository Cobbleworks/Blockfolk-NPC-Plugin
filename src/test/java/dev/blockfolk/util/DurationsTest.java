package dev.blockfolk.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import dev.blockfolk.runtime.ShopEconomy;

class DurationsTest {
    @ParameterizedTest
    @CsvSource({"0,never", "45,45 min", "60,1 h", "90,1 h 30 min", "1440,1 d", "1530,1 d 1 h 30 min"})
    void minutesAreWrittenInLargestUnits(int minutes, String text) {
        assertEquals(text, Durations.minutes(minutes));
    }

    @Test
    void countdownsRoundUpToWholeMinutes() {
        assertEquals("under a minute", Durations.remaining(59_000));
        assertEquals("1 min", Durations.remaining(60_000));
        assertEquals("42 min", Durations.remaining(41 * 60_000 + 1));
    }

    @ParameterizedTest
    @CsvSource({"90,90", "90m,90", "90 min,90", "1h30m,90", "2h,120", "1d,1440", "1d 2h,1560", "0,0"})
    void typedDurationsAreRead(String text, int minutes) {
        assertEquals(minutes, Durations.parseMinutes(text));
    }

    @ParameterizedTest
    @CsvSource({"''", "abc", "1x", "h", "-5", "1h abc"})
    void invalidDurationsAreRejected(String text) {
        assertEquals(-1, Durations.parseMinutes(text));
    }

    @ParameterizedTest
    @CsvSource(value = {"12|12.0", "12.50|12.5", "12,5|12.5", "1,000|1000.0", "1,000.25|1000.25",
            "0|0.0"}, delimiter = '|')
    void typedMoneyAcceptsCommonFormats(String text, double amount) {
        assertEquals(amount, ShopEconomy.parse(text));
    }

    @ParameterizedTest
    @CsvSource({"-1", "abc", "1.2.3", "''"})
    void invalidMoneyIsRejected(String text) {
        assertEquals(-1, ShopEconomy.parse(text));
    }

    @Test
    void plainAmountsDropTrailingZeros() {
        assertEquals("12", ShopEconomy.plain(12));
        assertEquals("12.5", ShopEconomy.plain(12.5));
        assertEquals("12.25", ShopEconomy.plain(12.25));
    }
}
