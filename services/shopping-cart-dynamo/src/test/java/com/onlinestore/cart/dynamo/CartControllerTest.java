package com.onlinestore.cart.dynamo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;

class CartControllerTest {

    private static final String UTC_SECONDS = "^\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\dZ$";

    private final FakeDynamoDb dynamo = new FakeDynamoDb();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new CartController(dynamo, new CartTable("carts"))).build();
    }

    @Test
    void createStoresOneItemPerCart() throws Exception {
        String id = createCart(7);

        assertThat(id).matches("[0-9a-f-]{36}");
        Map<String, AttributeValue> stored = dynamo.items.get(id);
        assertThat(stored.get("cart_id").s()).isEqualTo(id);
        assertThat(stored.get("customer_id").n()).isEqualTo("7");
        assertThat(stored.get("created_at").s()).matches(UTC_SECONDS);
        assertThat(stored.get("items").l()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{bad", "[]", "{\"customer_id\":\"5\"}", "{\"customer_id\":1.5}"})
    void rejectsUnreadableCreateBodies(String body) throws Exception {
        expectError(postJson("/shopping-carts", body), 400, "INVALID_INPUT", "Invalid request body");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"customer_id\":0}", "{\"customer_id\":-3}", "{}", "null"})
    void customerIdMustBePositive(String body) throws Exception {
        expectError(postJson("/shopping-carts", body), 400, "INVALID_INPUT", "customer_id must be a positive integer");
    }

    @Test
    void createFailureIncludesTheDynamoDbError() throws Exception {
        dynamo.putFailure = ResourceNotFoundException.builder().message("Requested resource not found").build();

        expectError(postJson("/shopping-carts", "{\"customer_id\":1}"), 500, "DYNAMODB_ERROR",
                "Failed to create cart: Requested resource not found");
    }

    @Test
    void getReturnsTheStoredCart() throws Exception {
        String id = createCart(7);

        mvc.perform(get("/shopping-carts/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shopping_cart_id").value(id))
                .andExpect(jsonPath("$.customer_id").value(7))
                .andExpect(jsonPath("$.created_at").value(Matchers.matchesPattern(UTC_SECONDS)))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items").isEmpty());
        expectError(mvc.perform(get("/shopping-carts/nope")), 404, "NOT_FOUND", "Cart nope not found");
    }

    @Test
    void addingTheSameProductIncreasesItsQuantity() throws Exception {
        String id = createCart(1);

        postJson("/shopping-carts/" + id + "/items", "{\"product_id\":10,\"quantity\":2}").andExpect(status().isNoContent());
        postJson("/shopping-carts/" + id + "/items", "{\"product_id\":11,\"quantity\":1}").andExpect(status().isNoContent());
        postJson("/shopping-carts/" + id + "/items", "{\"product_id\":10,\"quantity\":3}").andExpect(status().isNoContent());

        mvc.perform(get("/shopping-carts/" + id))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].product_id").value(10))
                .andExpect(jsonPath("$.items[0].quantity").value(5))
                .andExpect(jsonPath("$.items[0].added_at").value(Matchers.matchesPattern(UTC_SECONDS)))
                .andExpect(jsonPath("$.items[1].product_id").value(11))
                .andExpect(jsonPath("$.items[1].quantity").value(1));
    }

    @Test
    void validatesAddItemBeforeTouchingDynamoDb() throws Exception {
        dynamo.getFailure = new IllegalStateException("must not be called");

        expectError(postJson("/shopping-carts/x/items", "{bad"), 400, "INVALID_INPUT", "Invalid request body");
        expectError(postJson("/shopping-carts/x/items", "{\"product_id\":1,\"quantity\":0}"), 400, "INVALID_INPUT",
                "product_id and quantity must be positive integers");
    }

    @Test
    void addItemReportsEachFailingStep() throws Exception {
        expectError(postJson("/shopping-carts/missing/items", "{\"product_id\":1,\"quantity\":1}"), 404, "NOT_FOUND",
                "Cart missing not found");

        String id = createCart(1);
        dynamo.putFailure = new IllegalStateException("throttled");
        expectError(postJson("/shopping-carts/" + id + "/items", "{\"product_id\":1,\"quantity\":1}"), 500,
                "DYNAMODB_ERROR", "Failed to update cart: throttled");

        dynamo.getFailure = new IllegalStateException("timeout");
        expectError(postJson("/shopping-carts/" + id + "/items", "{\"product_id\":1,\"quantity\":1}"), 500,
                "DYNAMODB_ERROR", "Failed to retrieve cart");
        expectError(mvc.perform(get("/shopping-carts/" + id)), 500, "DYNAMODB_ERROR", "Failed to retrieve cart");
    }

    @Test
    void badlyShapedStoredItemsAreUnmarshalErrors() throws Exception {
        dynamo.items.put("broken", Map.of("cart_id", AttributeValue.fromS("broken"),
                "customer_id", AttributeValue.fromS("seven")));

        expectError(mvc.perform(get("/shopping-carts/broken")), 500, "UNMARSHAL_ERROR", "Failed to parse cart data");
        expectError(postJson("/shopping-carts/broken/items", "{\"product_id\":1,\"quantity\":1}"), 500,
                "UNMARSHAL_ERROR", "Failed to parse cart data");
    }

    @Test
    void cartWithoutAnItemListGetsOneOnFirstAdd() throws Exception {
        dynamo.items.put("old", Map.of("cart_id", AttributeValue.fromS("old"), "customer_id", AttributeValue.fromN("3"),
                "items", AttributeValue.fromNul(true)));

        mvc.perform(get("/shopping-carts/old")).andExpect(jsonPath("$.items").value(Matchers.nullValue()));
        postJson("/shopping-carts/old/items", "{\"product_id\":4,\"quantity\":2}").andExpect(status().isNoContent());
        mvc.perform(get("/shopping-carts/old")).andExpect(jsonPath("$.items[0].quantity").value(2));
    }

    @Test
    void healthChecksTheTable() throws Exception {
        mvc.perform(get("/health")).andExpect(content().json("{\"status\":\"healthy\"}", true));

        dynamo.describeFailure = ResourceNotFoundException.builder().message("Cannot do operations on a non-existent table").build();
        mvc.perform(get("/health")).andExpect(status().isServiceUnavailable()).andExpect(content().json(
                "{\"status\":\"unhealthy\",\"error\":\"Cannot do operations on a non-existent table\"}", true));
    }

    private String createCart(long customerId) throws Exception {
        String body = postJson("/shopping-carts", "{\"customer_id\":" + customerId + "}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new ObjectMapper().readTree(body).get("shopping_cart_id").asText();
    }

    private ResultActions postJson(String path, String body) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static void expectError(ResultActions result, int status, String code, String message) throws Exception {
        result.andExpect(status().is(status))
                .andExpect(content().json("{\"error\":\"" + code + "\",\"message\":\"" + message + "\"}", true));
    }

    @Test
    void storedAttributesHaveTheDocumentedTypes() {
        Map<String, AttributeValue> item = CartCodec.toItem(new com.onlinestore.cart.dynamo.model.Cart("c-1", 5,
                "2026-03-23T04:00:24Z", List.of(new com.onlinestore.cart.dynamo.model.CartItem(9, 2, "2026-03-23T04:00:30Z"))));

        assertThat(item.get("items").l().get(0).m())
                .containsEntry("product_id", AttributeValue.fromN("9"))
                .containsEntry("quantity", AttributeValue.fromN("2"))
                .containsEntry("added_at", AttributeValue.fromS("2026-03-23T04:00:30Z"));
    }
}
