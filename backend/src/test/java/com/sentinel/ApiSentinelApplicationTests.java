package com.sentinel;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ApiSentinelApplicationTests {

    @Test
    void contextLoads() {
        // Verifies Spring application context boots cleanly
    }
}
