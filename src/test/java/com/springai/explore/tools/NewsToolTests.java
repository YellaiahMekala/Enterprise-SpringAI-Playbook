package com.springai.explore.tools;

import com.springai.explore.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NewsToolTests {

    private static AppProperties props(String apiKey) {
        return new AppProperties(
                new AppProperties.Security(Map.of()),
                new AppProperties.Chat(20),
                new AppProperties.Tools(
                        new AppProperties.Tools.Weather("https://wttr.in", Duration.ofSeconds(5)),
                        new AppProperties.Tools.News("https://newsapi.org/v2", apiKey, 5, Duration.ofSeconds(5))),
                new AppProperties.Image("target/test-images"));
    }

    @Test
    void sendsApiKeyInHeaderNeverInTheUrl() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://newsapi.org/v2");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(containsString("/everything")))
                .andExpect(requestTo(not(containsString("secret-key"))))
                .andExpect(header("X-Api-Key", "secret-key"))
                .andExpect(queryParam("pageSize", "5"))
                .andRespond(withSuccess("{\"articles\":[]}", MediaType.APPLICATION_JSON));

        String result = new NewsTool(builder.build(), props("secret-key")).getLatestNews("AI regulation");

        assertThat(result).isEqualTo("{\"articles\":[]}");
        server.verify();
    }

    @Test
    void reportsNotConfiguredWithoutCallingTheProvider() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://newsapi.org/v2");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        assertThat(new NewsTool(builder.build(), props("")).getLatestNews("AI")).contains("not configured");
        server.verify();
    }

    @Test
    void degradesGracefullyWhenTheProviderFails() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://newsapi.org/v2");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(containsString("/everything"))).andRespond(withServerError());

        assertThat(new NewsTool(builder.build(), props("k")).getLatestNews("AI")).contains("unavailable");
    }
}
