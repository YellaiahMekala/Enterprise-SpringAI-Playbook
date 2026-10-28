package com.springai.explore.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class WeatherToolTests {

    private MockRestServiceServer server;
    private WeatherTool tool;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://wttr.in");
        server = MockRestServiceServer.bindTo(builder).build();
        tool = new WeatherTool(builder.build());
    }

    @Test
    void returnsProviderTextForAValidCity() {
        server.expect(requestTo("https://wttr.in/Hyderabad?format=3"))
                .andRespond(withSuccess("Hyderabad: 30C", MediaType.TEXT_PLAIN));

        assertThat(tool.getCurrentWeather("Hyderabad")).isEqualTo("Hyderabad: 30C");
        server.verify();
    }

    @Test
    void encodesTheLocationSoInputCannotChangeTheRequest() {
        server.expect(requestTo("https://wttr.in/New%20York?format=3"))
                .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));

        tool.getCurrentWeather("New York");
        server.verify();
    }

    @Test
    void rejectsSuspiciousLocationsWithoutCallingTheProvider() {
        assertThat(tool.getCurrentWeather("../../admin?x=1")).isEqualTo("Invalid location.");
        assertThat(tool.getCurrentWeather(null)).isEqualTo("Invalid location.");
        server.verify(); // no request was expected, none was made
    }

    @Test
    void degradesGracefullyWhenTheProviderFails() {
        server.expect(requestTo("https://wttr.in/Pune?format=3")).andRespond(withServerError());

        assertThat(tool.getCurrentWeather("Pune")).contains("unavailable");
    }
}
