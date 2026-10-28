package com.springai.explore.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Spring AI requires unique tool names, so the zone-specific variant has its own method name.
 */
@Component
public class DateTimeTool {

    private static final Logger log = LoggerFactory.getLogger(DateTimeTool.class);

    private final Clock clock;

    public DateTimeTool(Clock clock) {
        this.clock = clock;
    }

    @Tool(description = "Get the current date and time in the server's default time zone")
    public String getCurrentDateTime() {
        log.debug("Tool getCurrentDateTime invoked");
        return ZonedDateTime.now(clock).toString();
    }

    @Tool(description = "Get the current date and time for a given IANA time zone, for example Asia/Kolkata or America/New_York")
    public String getCurrentDateTimeZone(
            @ToolParam(description = "IANA time zone id, for example Asia/Tokyo") String timeZone) {
        log.debug("Tool getCurrentDateTimeZone invoked");
        try {
            return ZonedDateTime.now(clock.withZone(ZoneId.of(timeZone))).toString();
        } catch (DateTimeException | NullPointerException e) {
            // Tools report problems as data so the model can recover instead of failing the whole request.
            return "Unknown time zone. Use an IANA id such as Asia/Kolkata.";
        }
    }
}
