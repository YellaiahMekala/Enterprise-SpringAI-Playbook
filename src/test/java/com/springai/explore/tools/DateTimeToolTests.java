package com.springai.explore.tools;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class DateTimeToolTests {

    private final DateTimeTool tool =
            new DateTimeTool(Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void returnsTimeInTheRequestedZone() {
        assertThat(tool.getCurrentDateTimeZone("Asia/Kolkata")).startsWith("2026-01-01T05:30");
    }

    @Test
    void reportsUnknownZoneAsDataInsteadOfThrowing() {
        assertThat(tool.getCurrentDateTimeZone("Mars/Olympus")).contains("Unknown time zone");
        assertThat(tool.getCurrentDateTimeZone(null)).contains("Unknown time zone");
    }

    @Test
    void defaultVariantUsesTheInjectedClock() {
        assertThat(tool.getCurrentDateTime()).startsWith("2026-01-01T00:00");
    }
}
