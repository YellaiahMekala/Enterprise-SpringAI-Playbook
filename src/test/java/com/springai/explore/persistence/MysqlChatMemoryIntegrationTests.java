package com.springai.explore.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs against a real MySQL 8.4 container. Skipped automatically when Docker is not available. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("test")
class MysqlChatMemoryIntegrationTests {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Autowired
    ChatMemoryRepository repository;

    @Test
    void conversationSurvivesRoundTripThroughMySql() {
        repository.saveAll("alice", List.of(new UserMessage("My name is Asha"), new AssistantMessage("Hello Asha")));

        assertThat(repository.findByConversationId("alice")).extracting("text")
                .containsExactly("My name is Asha", "Hello Asha");
        assertThat(repository.findByConversationId("bob")).isEmpty();

        repository.deleteByConversationId("alice");
        assertThat(repository.findByConversationId("alice")).isEmpty();
    }
}
