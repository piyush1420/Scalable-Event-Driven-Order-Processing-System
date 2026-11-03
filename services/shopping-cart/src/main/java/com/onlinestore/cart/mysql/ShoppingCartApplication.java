package com.onlinestore.cart.mysql;

import java.sql.SQLException;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

@SpringBootApplication
public class ShoppingCartApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShoppingCartApplication.class, args);
    }

    @Bean(destroyMethod = "close")
    HikariDataSource dataSource(@Value("${db.url}") String url, @Value("${db.user}") String user,
            @Value("${db.password}") String password) {
        if (url.isBlank()) {
            throw new IllegalStateException("DB_URL environment variable is required");
        }
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(user);
        config.setPassword(password);
        // Pool tuned for db.t3.micro (~60 max connections): keep 10 to leave room for other services
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(5);
        config.setMaxLifetime(Duration.ofMinutes(5).toMillis());
        // Start even if the database is not reachable yet; DatabaseStartup waits for it
        config.setInitializationFailTimeout(-1);
        // Timestamps are read and written in UTC
        config.addDataSourceProperty("connectionTimeZone", "UTC");
        config.addDataSourceProperty("forceConnectionTimeZoneToSession", "true");
        return new HikariDataSource(config);
    }

    @Bean
    JdbcCartRepository cartRepository(HikariDataSource dataSource) {
        DatabaseStartup.waitForDatabase(dataSource.getJdbcUrl(), dataSource.getUsername(), dataSource.getPassword(),
                30, Duration.ofSeconds(2));
        JdbcCartRepository repository = new JdbcCartRepository(dataSource);
        try {
            repository.initSchema();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize schema: " + e.getMessage(), e);
        }
        return repository;
    }
}
