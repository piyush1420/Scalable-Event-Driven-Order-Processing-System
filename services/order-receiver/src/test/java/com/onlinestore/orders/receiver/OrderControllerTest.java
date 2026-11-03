package com.onlinestore.orders.receiver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

class OrderControllerTest {

    private static final String ORDER = "{\"customer_id\":42,\"items\":[{\"product_id\":\"PROD-1\","
            + "\"name\":\"Item-1\",\"quantity\":2,\"price\":19.99}]}";
    private static final String UUID_PATTERN = "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$";

    private final ObjectMapper mapper = Jackson2ObjectMapperBuilder.json()
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    private final List<Duration> sleeps = new ArrayList<>();
    private final List<String> published = new ArrayList<>();
    private RuntimeException publishFailure;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        OrderPublisher publisher = message -> {
            if (publishFailure != null) {
                throw publishFailure;
            }
            published.add(message);
        };
        OrderController controller = new OrderController(new PaymentProcessor(sleeps::add), publisher, mapper);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new StringHttpMessageConverter(), new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    @Test
    void syncOrderWaitsForPaymentThenCompletes() throws Exception {
        mvc.perform(post("/orders/sync").contentType(MediaType.APPLICATION_JSON).content(ORDER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order_id").value(org.hamcrest.Matchers.matchesPattern(UUID_PATTERN)))
                .andExpect(jsonPath("$.customer_id").value(42))
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.items[0].product_id").value("PROD-1"))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].price").value(19.99))
                .andExpect(jsonPath("$.created_at").value(org.hamcrest.Matchers.matchesPattern("^\\d{4}-\\d\\d-\\d\\dT.*Z$")));

        assertThat(sleeps).containsExactly(Duration.ofSeconds(3));
        assertThat(published).isEmpty();
    }

    @Test
    void asyncOrderIsPublishedAndAcceptedWithoutWaiting() throws Exception {
        MvcResult result = mvc.perform(post("/orders/async").contentType(MediaType.APPLICATION_JSON).content(ORDER))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("pending"))
                .andReturn();

        assertThat(sleeps).isEmpty();
        assertThat(published).hasSize(1);
        JsonNode message = mapper.readTree(published.get(0));
        JsonNode response = mapper.readTree(result.getResponse().getContentAsString());
        assertThat(message).isEqualTo(response);
        assertThat(published.get(0)).startsWith("{\"order_id\":\"").contains(
                "\"customer_id\":42,\"status\":\"pending\",\"items\":[{\"product_id\":\"PROD-1\",\"name\":\"Item-1\","
                        + "\"quantity\":2,\"price\":19.99}],\"created_at\":\"");
    }

    @Test
    void publishFailureIsReportedAsServerError() throws Exception {
        publishFailure = new IllegalStateException("SNS is down");

        mvc.perform(post("/orders/async").contentType(MediaType.APPLICATION_JSON).content(ORDER))
                .andExpect(status().isInternalServerError())
                .andExpect(content().json("{\"error\":\"Failed to queue order\"}", true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{bad", "[]", "{\"customer_id\":\"5\"}", "{\"customer_id\":1.5}", "{\"items\":{}}",
            "{\"items\":[{\"price\":\"9.99\"}]}", "{\"items\":[{\"quantity\":1.5}]}", "{\"items\":[{\"product_id\":7}]}",
            "{\"items\":[{\"price\":1e400}]}"})
    void invalidBodiesAreRejectedOnBothEndpoints(String body) throws Exception {
        for (String path : new String[] {"/orders/sync", "/orders/async"}) {
            mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().json("{\"error\":\"Invalid request body\"}", true));
        }
        assertThat(sleeps).isEmpty();
        assertThat(published).isEmpty();
    }

    @Test
    void nullBodyAndNullItemsBecomeEmptyValues() throws Exception {
        mvc.perform(post("/orders/sync").contentType(MediaType.APPLICATION_JSON).content("null"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customer_id").value(0))
                .andExpect(jsonPath("$.items").value(org.hamcrest.Matchers.nullValue()));

        mvc.perform(post("/orders/async").contentType(MediaType.APPLICATION_JSON).content("{\"items\":[null]}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.items[0].product_id").value(""))
                .andExpect(jsonPath("$.items[0].quantity").value(0));
    }

    @Test
    void health() throws Exception {
        mvc.perform(get("/health")).andExpect(content().json("{\"status\":\"healthy\"}", true));
    }
}
