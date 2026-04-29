package com.example.demo.graph.config;

import java.util.concurrent.TimeUnit;
import org.neo4j.driver.Config;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(Neo4jGraphProperties.class)
public class GraphConfig {

    private static final Logger log = LoggerFactory.getLogger(GraphConfig.class);

    @Bean(destroyMethod = "close")
    Driver neo4jDriver(Neo4jGraphProperties properties) {
        String uri = normalizeUri(properties.getUri());
        Config config = Config.builder()
            .withConnectionTimeout(properties.getConnectionTimeoutSeconds(), TimeUnit.SECONDS)
            .withConnectionAcquisitionTimeout(properties.getAcquisitionTimeoutSeconds(), TimeUnit.SECONDS)
            .withMaxTransactionRetryTime(properties.getMaxTransactionRetrySeconds(), TimeUnit.SECONDS)
            .withConnectionLivenessCheckTimeout(properties.getLivenessCheckSeconds(), TimeUnit.SECONDS)
            .withMaxConnectionPoolSize(properties.getMaxConnectionPoolSize())
            .build();

        if (properties.getUsername() == null || properties.getUsername().isBlank()) {
            return GraphDatabase.driver(uri, AuthTokens.none(), config);
        }

        return GraphDatabase.driver(
            uri,
            AuthTokens.basic(properties.getUsername(), properties.getPassword()),
            config
        );
    }

    private String normalizeUri(String uri) {
        if (uri == null || uri.isBlank()) {
            return uri;
        }

        if (uri.startsWith("neo4j+s://")) {
            log.warn("Neo4j URI '{}' uses routing discovery. Falling back to single-instance secure bolt mode.", uri);
            return "bolt+s://" + uri.substring("neo4j+s://".length());
        }
        if (uri.startsWith("neo4j+ssc://")) {
            log.warn("Neo4j URI '{}' uses routing discovery. Falling back to single-instance self-signed bolt mode.", uri);
            return "bolt+ssc://" + uri.substring("neo4j+ssc://".length());
        }
        if (uri.startsWith("neo4j://")) {
            log.warn("Neo4j URI '{}' uses routing discovery. Falling back to single-instance bolt mode.", uri);
            return "bolt://" + uri.substring("neo4j://".length());
        }
        return uri;
    }
}
