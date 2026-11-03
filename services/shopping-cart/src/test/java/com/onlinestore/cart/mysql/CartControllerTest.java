package com.onlinestore.cart.mysql;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.onlinestore.cart.mysql.model.CartItemView;
import com.onlinestore.cart.mysql.model.CartView;

class CartControllerTest {

    private static final Instant CREATED = Instant.parse("2026-03-23T04:00:24Z");

    private final FakeRepository carts = new FakeRepository();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        MappingJackson2HttpMessageConverter json = new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder
                .json().featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build());
        mvc = MockMvcBuilders.standaloneSetup(new CartController(carts))
                .setMessageConverters(new StringHttpMessageConverter(), json)
                .build();
    }

    @Test
    void createsCartAndReturnsItsId() throws Exception {
        postJson("/shopping-carts", "{\"customer_id\":7}")
                .andExpect(status().isCreated())
                .andExpect(content().json("{\"shopping_cart_id\":1}", true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{bad", "[]", "{\"customer_id\":\"5\"}", "{\"customer_id\":1.5}",
            "{\"customer_id\":true}"})
    void rejectsUnreadableCreateBodies(String body) throws Exception {
        postJson("/shopping-carts", body).andExpect(status().isBadRequest())
                .andExpect(content().json("{\"error\":\"INVALID_INPUT\",\"message\":\"Invalid request body\"}", true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"customer_id\":0}", "{\"customer_id\":-3}", "{}", "null", "{\"customer_id\":null}"})
    void customerIdMustBePositive(String body) throws Exception {
        postJson("/shopping-carts", body).andExpect(status().isBadRequest()).andExpect(content().json(
                "{\"error\":\"INVALID_INPUT\",\"message\":\"customer_id must be a positive integer\"}", true));
    }

    @Test
    void returnsCartWithItems() throws Exception {
        carts.carts.put(1L, new CartView(1, 7, CREATED, List.of(
                new CartItemView(1, 10, 5, CREATED), new CartItemView(3, 11, 1, CREATED))));

        mvc.perform(get("/shopping-carts/1")).andExpect(status().isOk()).andExpect(content().json(
                "{\"shopping_cart_id\":1,\"customer_id\":7,\"created_at\":\"2026-03-23T04:00:24Z\",\"items\":["
                        + "{\"id\":1,\"product_id\":10,\"quantity\":5,\"added_at\":\"2026-03-23T04:00:24Z\"},"
                        + "{\"id\":3,\"product_id\":11,\"quantity\":1,\"added_at\":\"2026-03-23T04:00:24Z\"}]}", true));
        mvc.perform(get("/shopping-carts/+1")).andExpect(status().isOk());
    }

    @Test
    void reportsInvalidAndMissingCarts() throws Exception {
        for (String bad : new String[] {"abc", "0", "-1", "1.5"}) {
            mvc.perform(get("/shopping-carts/" + bad)).andExpect(status().isBadRequest())
                    .andExpect(content().json("{\"error\":\"INVALID_INPUT\",\"message\":\"Invalid cart ID\"}", true));
        }
        mvc.perform(get("/shopping-carts/99")).andExpect(status().isNotFound())
                .andExpect(content().json("{\"error\":\"NOT_FOUND\",\"message\":\"Cart 99 not found\"}", true));
    }

    @Test
    void addsItemsToAnExistingCart() throws Exception {
        carts.carts.put(1L, new CartView(1, 7, CREATED, new ArrayList<>()));

        postJson("/shopping-carts/1/items", "{\"product_id\":10,\"quantity\":2}").andExpect(status().isNoContent());

        mvc.perform(get("/shopping-carts/1")).andExpect(content().json("{\"items\":[{\"product_id\":10,\"quantity\":2}]}"));
    }

    @Test
    void validatesAddItemInTheOriginalOrder() throws Exception {
        expectError(postJson("/shopping-carts/abc/items", "{bad"), 400, "INVALID_INPUT", "Invalid cart ID");
        expectError(postJson("/shopping-carts/99/items", "{bad"), 400, "INVALID_INPUT", "Invalid request body");
        expectError(postJson("/shopping-carts/99/items", "{\"product_id\":0,\"quantity\":1}"), 400, "INVALID_INPUT",
                "product_id and quantity must be positive integers");
        expectError(postJson("/shopping-carts/99/items", "{\"product_id\":1}"), 400, "INVALID_INPUT",
                "product_id and quantity must be positive integers");
        expectError(postJson("/shopping-carts/99/items", "{\"product_id\":1,\"quantity\":1}"), 404, "NOT_FOUND",
                "Cart 99 not found");
    }

    @Test
    void databaseFailuresReturnTheStepThatFailed() throws Exception {
        carts.failure = new CartRepository.DatabaseException("Failed to verify cart", null);
        expectError(postJson("/shopping-carts/1/items", "{\"product_id\":1,\"quantity\":1}"), 500, "DATABASE_ERROR",
                "Failed to verify cart");

        carts.failure = new CartRepository.DatabaseException("Failed to create cart", null);
        expectError(postJson("/shopping-carts", "{\"customer_id\":1}"), 500, "DATABASE_ERROR", "Failed to create cart");

        carts.failure = new CartRepository.DatabaseException("Failed to retrieve cart", null);
        expectError(mvc.perform(get("/shopping-carts/1")), 500, "DATABASE_ERROR", "Failed to retrieve cart");
    }

    @Test
    void healthReflectsTheDatabase() throws Exception {
        mvc.perform(get("/health")).andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"healthy\"}", true));

        carts.pingFailure = new IllegalStateException("connection refused");
        mvc.perform(get("/health")).andExpect(status().isServiceUnavailable())
                .andExpect(content().json("{\"status\":\"unhealthy\",\"error\":\"connection refused\"}", true));
    }

    private ResultActions postJson(String path, String body) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static void expectError(ResultActions result, int status, String code, String message) throws Exception {
        result.andExpect(status().is(status))
                .andExpect(content().json("{\"error\":\"" + code + "\",\"message\":\"" + message + "\"}", true));
    }

    /** In-memory repository with switchable failures. */
    private static final class FakeRepository implements CartRepository {
        final Map<Long, CartView> carts = new HashMap<>();
        DatabaseException failure;
        RuntimeException pingFailure;

        @Override
        public long createCart(long customerId) throws DatabaseException {
            failIfAsked();
            long id = carts.size() + 1;
            carts.put(id, new CartView(id, customerId, CREATED, new ArrayList<>()));
            return id;
        }

        @Override
        public Optional<CartView> findCart(long cartId) throws DatabaseException {
            failIfAsked();
            return Optional.ofNullable(carts.get(cartId));
        }

        @Override
        public boolean addItem(long cartId, long productId, long quantity) throws DatabaseException {
            failIfAsked();
            CartView cart = carts.get(cartId);
            if (cart == null) {
                return false;
            }
            cart.items().add(new CartItemView(cart.items().size() + 1, productId, quantity, CREATED));
            return true;
        }

        @Override
        public void ping() {
            if (pingFailure != null) {
                throw pingFailure;
            }
        }

        private void failIfAsked() throws DatabaseException {
            if (failure != null) {
                throw failure;
            }
        }
    }
}
