package com.springai.explore.tools;

import com.springai.explore.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class NewsTool {

    private static final Logger log = LoggerFactory.getLogger(NewsTool.class);
    /** Bounds the tokens a single tool result can add to the prompt. */
    private static final int MAX_RESULT_CHARS = 6000;

    private final RestClient restClient;
    private final AppProperties.Tools.News config;

    public NewsTool(@Qualifier("newsRestClient") RestClient restClient, AppProperties props) {
        this.restClient = restClient;
        this.config = props.tools().news();
    }

    @Tool(description = "Get the latest news headlines about a topic")
    public String getLatestNews(@ToolParam(description = "Topic to search for, for example 'AI regulation'") String topic) {
        if (config.apiKey().isBlank()) {
            return "News service is not configured.";
        }
        if (topic == null || topic.isBlank() || topic.length() > 100) {
            return "Invalid topic.";
        }
        log.debug("Tool getLatestNews invoked");
        try {
            String body = restClient.get()
                    .uri(uri -> uri.path("/everything")
                            .queryParam("q", "{q}")
                            .queryParam("pageSize", config.pageSize())
                            .queryParam("sortBy", "publishedAt")
                            .queryParam("language", "en")
                            .build(topic))
                    // Key goes in a header, never in the URL, so it cannot leak into access or proxy logs.
                    .header("X-Api-Key", config.apiKey())
                    .retrieve()
                    .body(String.class);
            return body != null && body.length() > MAX_RESULT_CHARS ? body.substring(0, MAX_RESULT_CHARS) : body;
        } catch (RestClientException e) {
            log.warn("News lookup failed: {}", e.getMessage());
            return "News service is currently unavailable.";
        }
    }
}
