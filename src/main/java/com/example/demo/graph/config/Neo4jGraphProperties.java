package com.example.demo.graph.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.graph.neo4j")
public class Neo4jGraphProperties {

    private String uri;
    private String username;
    private String password;
    private String database;
    private int connectionTimeoutSeconds = 5;
    private int acquisitionTimeoutSeconds = 10;
    private int maxTransactionRetrySeconds = 3;
    private int livenessCheckSeconds = 30;
    private int maxConnectionPoolSize = 20;

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public int getConnectionTimeoutSeconds() {
        return connectionTimeoutSeconds;
    }

    public void setConnectionTimeoutSeconds(int connectionTimeoutSeconds) {
        this.connectionTimeoutSeconds = connectionTimeoutSeconds;
    }

    public int getAcquisitionTimeoutSeconds() {
        return acquisitionTimeoutSeconds;
    }

    public void setAcquisitionTimeoutSeconds(int acquisitionTimeoutSeconds) {
        this.acquisitionTimeoutSeconds = acquisitionTimeoutSeconds;
    }

    public int getMaxTransactionRetrySeconds() {
        return maxTransactionRetrySeconds;
    }

    public void setMaxTransactionRetrySeconds(int maxTransactionRetrySeconds) {
        this.maxTransactionRetrySeconds = maxTransactionRetrySeconds;
    }

    public int getLivenessCheckSeconds() {
        return livenessCheckSeconds;
    }

    public void setLivenessCheckSeconds(int livenessCheckSeconds) {
        this.livenessCheckSeconds = livenessCheckSeconds;
    }

    public int getMaxConnectionPoolSize() {
        return maxConnectionPoolSize;
    }

    public void setMaxConnectionPoolSize(int maxConnectionPoolSize) {
        this.maxConnectionPoolSize = maxConnectionPoolSize;
    }
}
