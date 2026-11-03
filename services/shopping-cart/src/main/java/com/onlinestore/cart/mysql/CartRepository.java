package com.onlinestore.cart.mysql;

import java.util.Optional;

import com.onlinestore.cart.mysql.model.CartView;

/** Cart storage. Every failure carries the user-facing message for the step that failed. */
public interface CartRepository {

    long createCart(long customerId) throws DatabaseException;

    Optional<CartView> findCart(long cartId) throws DatabaseException;

    /** Adds the quantity to the product's line (creating it if needed); false if the cart does not exist. */
    boolean addItem(long cartId, long productId, long quantity) throws DatabaseException;

    /** Throws if the database cannot be reached. */
    void ping() throws Exception;

    class DatabaseException extends Exception {
        public DatabaseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
