package com.springai.explore.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.regex.Pattern;

@Component
public class WeatherTool {

    private static final Logger log = LoggerFactory.getLogger(WeatherTool.class);
    private static final Pattern VALID_LOCATION = Pattern.compile("[\\p{L}\\p{N} ,.'-]{1,80}");

    private final RestClient restClient;

    public WeatherTool(@Qualifier("weatherRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Tool(description = "Get the current weather for a city")
    public String getCurrentWeather(
            @ToolParam(description = "City name, for example Hyderabad") String location) {
        if (location == null || !VALID_LOCATION.matcher(location).matches()) {
            return "Invalid location.";
        }
        log.debug("Tool getCurrentWeather invoked");
        try {
            // URI variable is percent-encoded by the template, so user input cannot alter the path or query.
            return restClient.get().uri("/{location}?format=3", location).retrieve().body(String.class);
        } catch (RestClientException e) {
            log.warn("Weather lookup failed: {}", e.getMessage());
            return "Weather service is currently unavailable.";
        }
    }
}
