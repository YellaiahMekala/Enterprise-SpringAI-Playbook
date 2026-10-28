package com.springai.explore;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ApplicationContextTests {

    @Autowired
    ChatMemory chatMemory;

    @Test
    void contextLoadsWithJdbcBackedMemory() {
        assertThat(chatMemory).isNotNull();
    }
}
