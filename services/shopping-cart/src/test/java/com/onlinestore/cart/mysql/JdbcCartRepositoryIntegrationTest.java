package com.onlinestore.cart.mysql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.onlinestore.cart.mysql.model.CartItemView;
import com.onlinestore.cart.mysql.model.CartView;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Runs the real SQL against a MySQL server. Skipped unless TEST_MYSQL_URL is set, for example:
 * <pre>
 *   docker run -d --name mysql-test -p 3307:3306 -e MYSQL_ROOT_PASSWORD=test -e MYSQL_DATABASE=cart_test mysql:8.4
 *   TEST_MYSQL_URL=jdbc:mysql://localhost:3307/cart_test TEST_MYSQL_PASSWORD=test ./mvnw -pl services/shopping-cart test
 * </pre>
 * The test drops and recreates the cart tables, so never point it at a real database.
 */
@EnabledIfEnvironmentVariable(named = "TEST_MYSQL_URL", matches = ".+")
class JdbcCartRepositoryIntegrationTest {

    private static HikariDataSource dataSource;
    private JdbcCartRepository repository;

    @BeforeAll
    static void connect() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(System.getenv("TEST_MYSQL_URL"));
        config.setUsername(System.getenv().getOrDefault("TEST_MYSQL_USER", "root"));
        config.setPassword(System.getenv().getOrDefault("TEST_MYSQL_PASSWORD", ""));
        config.addDataSourceProperty("connectionTimeZone", "UTC");
        config.addDataSourceProperty("forceConnectionTimeZoneToSession", "true");
        dataSource = new HikariDataSource(config);
    }

    @AfterAll
    static void disconnect() {
        dataSource.close();
    }

    @BeforeEach
    void freshTables() throws Exception {
        try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
            st.execute("DROP TABLE IF EXISTS cart_items");
            st.execute("DROP TABLE IF EXISTS carts");
        }
        repository = new JdbcCartRepository(dataSource);
        repository.initSchema();
        repository.initSchema(); // creating the schema twice is harmless
    }

    @Test
    void createsAndReadsAnEmptyCart() throws Exception {
        long id = repository.createCart(42);

        CartView cart = repository.findCart(id).orElseThrow();
        assertThat(cart.shoppingCartId()).isEqualTo(id);
        assertThat(cart.customerId()).isEqualTo(42);
        assertThat(cart.items()).isEmpty();
        assertThat(Duration.between(cart.createdAt(), Instant.now()).abs()).isLessThan(Duration.ofMinutes(1));
    }

    @Test
    void addingTheSameProductAddsToItsQuantity() throws Exception {
        long id = repository.createCart(1);

        assertThat(repository.addItem(id, 10, 2)).isTrue();
        assertThat(repository.addItem(id, 10, 3)).isTrue();
        assertThat(repository.addItem(id, 11, 1)).isTrue();

        assertThat(repository.findCart(id).orElseThrow().items())
                .extracting(CartItemView::productId, CartItemView::quantity)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(10L, 5L), org.assertj.core.groups.Tuple.tuple(11L, 1L));
    }

    @Test
    void missingCartsAreReportedNotCreated() throws Exception {
        assertThat(repository.findCart(999)).isEmpty();
        assertThat(repository.addItem(999, 1, 1)).isFalse();
        assertThat(repository.findCart(999)).isEmpty();
    }

    @Test
    void valuesTooLargeForTheIntColumnsAreDatabaseErrors() throws Exception {
        long id = repository.createCart(1);

        assertThatThrownBy(() -> repository.createCart(99_999_999_999L)).hasMessage("Failed to create cart");
        assertThatThrownBy(() -> repository.addItem(id, 99_999_999_999L, 1)).hasMessage("Failed to add item to cart");
        assertThat(repository.findCart(99_999_999_999L)).isEmpty();
    }

    @Test
    void pingSucceedsAgainstALiveDatabase() throws Exception {
        repository.ping();
    }
}
