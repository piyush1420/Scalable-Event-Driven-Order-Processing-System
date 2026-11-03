package com.onlinestore.cart.mysql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Waits for the database at startup (RDS can still be booting when the container starts). Tries
 * up to {@code attempts} times, {@code delay} apart; afterwards startup continues and the schema
 * step reports the real error if the database never came up.
 */
public final class DatabaseStartup {

    private static final Logger log = LoggerFactory.getLogger(DatabaseStartup.class);

    private DatabaseStartup() {
    }

    public static void waitForDatabase(String url, String user, String password, int attempts, Duration delay) {
        Properties props = new Properties();
        props.setProperty("user", user);
        props.setProperty("password", password);
        props.setProperty("connectTimeout", "5000");
        for (int i = 0; i < attempts; i++) {
            try (Connection conn = DriverManager.getConnection(url, props)) {
                if (conn.isValid(2)) {
                    return;
                }
            } catch (SQLException notReadyYet) {
                // fall through to the retry below
            }
            log.info("Waiting for database...");
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
