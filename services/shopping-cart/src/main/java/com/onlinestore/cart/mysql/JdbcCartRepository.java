package com.onlinestore.cart.mysql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import com.onlinestore.cart.mysql.model.CartItemView;
import com.onlinestore.cart.mysql.model.CartView;

/**
 * MySQL implementation (version 2 of the HW8 design):
 * <ul>
 *   <li>single LEFT JOIN query to read a cart with its items</li>
 *   <li>UNIQUE(cart_id, product_id) plus ON DUPLICATE KEY UPDATE, so adding a product twice adds
 *       to its quantity instead of creating a duplicate row</li>
 *   <li>a transaction around "check the cart exists" and "upsert the item"</li>
 * </ul>
 */
public class JdbcCartRepository implements CartRepository {

    /** Value used when an item row has no timestamp (the zero time). */
    static final Instant ZERO_TIME = Instant.parse("0001-01-01T00:00:00Z");

    static final String CREATE_CARTS_TABLE = """
            CREATE TABLE IF NOT EXISTS carts (
                id INT AUTO_INCREMENT PRIMARY KEY,
                customer_id INT NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_customer_id (customer_id)
            )""";

    static final String CREATE_CART_ITEMS_TABLE = """
            CREATE TABLE IF NOT EXISTS cart_items (
                id INT AUTO_INCREMENT PRIMARY KEY,
                cart_id INT NOT NULL,
                product_id INT NOT NULL,
                quantity INT NOT NULL DEFAULT 1,
                added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (cart_id) REFERENCES carts(id) ON DELETE CASCADE,
                UNIQUE KEY uq_cart_product (cart_id, product_id),
                INDEX idx_cart_id (cart_id)
            )""";

    private static final String SELECT_CART = """
            SELECT c.id, c.customer_id, c.created_at,
                   ci.id, ci.product_id, ci.quantity, ci.added_at
            FROM carts c
            LEFT JOIN cart_items ci ON c.id = ci.cart_id
            WHERE c.id = ?""";

    private static final String UPSERT_ITEM = """
            INSERT INTO cart_items (cart_id, product_id, quantity)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE quantity = quantity + VALUES(quantity)""";

    private final DataSource dataSource;

    public JdbcCartRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Creates both tables if they do not exist yet. */
    public void initSchema() throws SQLException {
        try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
            st.execute(CREATE_CARTS_TABLE);
            st.execute(CREATE_CART_ITEMS_TABLE);
        }
    }

    @Override
    public long createCart(long customerId) throws DatabaseException {
        try (Connection conn = dataSource.getConnection();
                PreparedStatement insert = conn.prepareStatement("INSERT INTO carts (customer_id) VALUES (?)",
                        Statement.RETURN_GENERATED_KEYS)) {
            insert.setLong(1, customerId);
            insert.executeUpdate();
            try (ResultSet keys = insert.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to create cart", e);
        }
    }

    @Override
    public Optional<CartView> findCart(long cartId) throws DatabaseException {
        try (Connection conn = dataSource.getConnection(); PreparedStatement query = conn.prepareStatement(SELECT_CART)) {
            query.setLong(1, cartId);
            ResultSet rows;
            try {
                rows = query.executeQuery();
            } catch (SQLException e) {
                throw new DatabaseException("Failed to retrieve cart", e);
            }
            try (rows) {
                return readCart(cartId, rows);
            } catch (SQLException | IllegalStateException e) {
                throw new DatabaseException("Failed to parse cart data", e);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to retrieve cart", e);
        }
    }

    private static Optional<CartView> readCart(long cartId, ResultSet rows) throws SQLException {
        boolean found = false;
        long customerId = 0;
        Instant createdAt = null;
        List<CartItemView> items = new ArrayList<>();

        while (rows.next()) {
            if (!found) {
                customerId = rows.getLong(2);
                LocalDateTime created = rows.getObject(3, LocalDateTime.class);
                if (created == null) {
                    throw new IllegalStateException("cart " + cartId + " has no created_at");
                }
                createdAt = created.toInstant(ZoneOffset.UTC);
                found = true;
            }
            // Only add an item if the LEFT JOIN returned a real item row
            Long itemId = rows.getObject(4, Long.class);
            if (itemId != null) {
                Long productId = rows.getObject(5, Long.class);
                Long quantity = rows.getObject(6, Long.class);
                LocalDateTime addedAt = rows.getObject(7, LocalDateTime.class);
                items.add(new CartItemView(itemId,
                        productId == null ? 0 : productId,
                        quantity == null ? 0 : quantity,
                        addedAt == null ? ZERO_TIME : addedAt.toInstant(ZoneOffset.UTC)));
            }
        }
        return found ? Optional.of(new CartView(cartId, customerId, createdAt, items)) : Optional.empty();
    }

    @Override
    public boolean addItem(long cartId, long productId, long quantity) throws DatabaseException {
        Connection conn;
        try {
            conn = dataSource.getConnection();
        } catch (SQLException e) {
            throw new DatabaseException("Failed to start transaction", e);
        }
        try {
            conn.setAutoCommit(false);
        } catch (SQLException e) {
            closeQuietly(conn);
            throw new DatabaseException("Failed to start transaction", e);
        }

        try {
            if (!cartExists(conn, cartId)) {
                return false;
            }
            upsertItem(conn, cartId, productId, quantity);
            try {
                conn.commit();
            } catch (SQLException e) {
                throw new DatabaseException("Failed to commit transaction", e);
            }
            return true;
        } finally {
            // like a deferred rollback: discards unfinished work, does nothing after a commit
            rollbackQuietly(conn);
            closeQuietly(conn);
        }
    }

    private static boolean cartExists(Connection conn, long cartId) throws DatabaseException {
        try (PreparedStatement exists = conn.prepareStatement("SELECT EXISTS(SELECT 1 FROM carts WHERE id = ?)")) {
            exists.setLong(1, cartId);
            try (ResultSet rs = exists.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to verify cart", e);
        }
    }

    private static void upsertItem(Connection conn, long cartId, long productId, long quantity)
            throws DatabaseException {
        try (PreparedStatement upsert = conn.prepareStatement(UPSERT_ITEM)) {
            upsert.setLong(1, cartId);
            upsert.setLong(2, productId);
            upsert.setLong(3, quantity);
            upsert.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Failed to add item to cart", e);
        }
    }

    private static void rollbackQuietly(Connection conn) {
        try {
            conn.rollback();
        } catch (SQLException ignored) {
            // the connection is closed right after; the transaction is discarded anyway
        }
    }

    private static void closeQuietly(Connection conn) {
        try {
            conn.close();
        } catch (SQLException ignored) {
            // nothing useful to do; the pool replaces broken connections
        }
    }

    @Override
    public void ping() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            if (!conn.isValid(2)) {
                throw new SQLException("database connection is not valid");
            }
        }
    }
}
