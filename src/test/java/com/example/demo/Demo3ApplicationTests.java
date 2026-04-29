package com.example.demo;

import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class Demo3ApplicationTests {

    @MockitoBean
    private Driver neo4jDriver;

    @Test
    void contextLoads() {
    }

}
