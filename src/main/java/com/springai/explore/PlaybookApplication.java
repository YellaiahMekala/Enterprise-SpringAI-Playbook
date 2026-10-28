package com.springai.explore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

// Authentication is by API key only (see SecurityConfig), so Boot's default in-memory user with a
// generated password would be dead, misleading configuration.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class PlaybookApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlaybookApplication.class, args);
    }

}
