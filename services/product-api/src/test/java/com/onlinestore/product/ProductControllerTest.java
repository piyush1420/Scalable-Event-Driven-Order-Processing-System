package com.onlinestore.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.onlinestore.product.model.ProductWithInventory;
import com.onlinestore.product.resilience.Bulkhead;
import com.onlinestore.product.resilience.CircuitBreaker;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

class ProductControllerTest {

    private static final String PRODUCT_1 = "{\"id\":1,\"name\":\"Product Beta 1\",\"category\":\"Books\","
            + "\"description\":\"Premium grade item with warranty\",\"brand\":\"Beta\",\"sku\":\"SKU-000001\","
            + "\"manufacturer\":\"Beta\",\"category_id\":1,\"weight\":2,\"some_other_id\":2";

    private HttpServer inventoryStub;
    private final AtomicInteger inventoryCalls = new AtomicInteger();
    private volatile int stubStatus = 200;
    private volatile long stubDelayMillis;
    private volatile CountDownLatch holdRequests;

    private String inventoryUrl;
    private CircuitBreaker breaker;
    private Bulkhead bulkhead;
    private ResilientInventoryClient client;
    private MockMvc mvc;

    @BeforeEach
    void setUp() throws IOException {
        inventoryStub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        inventoryStub.createContext("/inventory/", this::serveInventory);
        inventoryStub.createContext("/mode/", exchange -> respond(exchange, 200,
                "{\"message\":\"Mode changed to crashed\",\"previous\":\"healthy\"}"));
        inventoryStub.setExecutor(Executors.newCachedThreadPool());
        inventoryStub.start();
        inventoryUrl = "http://127.0.0.1:" + inventoryStub.getAddress().getPort();

        breaker = new CircuitBreaker(5, 3, Duration.ofSeconds(15));
        bulkhead = new Bulkhead(10);
        client = new ResilientInventoryClient(inventoryUrl, Duration.ofMillis(300), breaker, bulkhead);
        mvc = MockMvcBuilders
                .standaloneSetup(new ProductController(new ProductStore(100_000), client, breaker, bulkhead))
                .build();
    }

    @AfterEach
    void tearDown() {
        try {
            inventoryStub.stop(0);
        } catch (IllegalStateException alreadyStopped) {
            // one test stops the stub on purpose
        }
    }

    @Test
    void returnsProductMergedWithItsStock() throws Exception {
        mvc.perform(get("/products/1"))
                .andExpect(status().isOk())
                .andExpect(content().json(PRODUCT_1
                        + ",\"stock\":59,\"warehouse\":\"US-West\",\"inventory_status\":\"in_stock\"}", true));
    }

    @Test
    void inventoryErrorStillReturnsTheProduct() throws Exception {
        stubStatus = 500;

        mvc.perform(get("/products/1"))
                .andExpect(status().isOk())
                .andExpect(content().json(PRODUCT_1
                        + ",\"stock\":-1,\"warehouse\":\"unknown\",\"inventory_status\":\"unavailable\"}", true));
        mvc.perform(get("/metrics")).andExpect(jsonPath("$.circuit_breaker.failures").value(1));
    }

    @Test
    void slowInventoryIsCutOffAtTheDeadline() throws Exception {
        stubDelayMillis = 2_000;
        long start = System.nanoTime();

        mvc.perform(get("/products/2")).andExpect(jsonPath("$.inventory_status").value("unavailable"));

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(1_500));
    }

    @Test
    void circuitOpensAfterFiveFailuresAndStopsCallingInventory() throws Exception {
        stubStatus = 503;
        for (int i = 1; i <= 6; i++) {
            mvc.perform(get("/products/" + i)).andExpect(jsonPath("$.inventory_status").value("unavailable"));
        }

        assertThat(inventoryCalls.get()).isEqualTo(5);
        mvc.perform(get("/metrics")).andExpect(content().json(
                "{\"circuit_breaker\":{\"state\":\"OPEN\",\"total_requests\":6,\"successes\":0,\"failures\":5,"
                        + "\"rejected_by_circuit\":1},\"bulkhead\":{\"max_concurrent\":10,\"rejected\":0}}", true));
    }

    @Test
    void bulkheadRejectsCallsBeyondTenInFlight() throws Exception {
        holdRequests = new CountDownLatch(1);
        ResilientInventoryClient patient =
                new ResilientInventoryClient(inventoryUrl, Duration.ofSeconds(5), breaker, bulkhead);
        ExecutorService pool = Executors.newFixedThreadPool(15);
        try {
            List<Future<ProductWithInventory>> calls = new ArrayList<>();
            for (int i = 1; i <= 15; i++) {
                long id = i;
                calls.add(pool.submit(() -> patient.withInventory(
                        new ProductStore(1).get(1).orElseThrow().withId(id))));
            }
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (bulkhead.rejected() < 5 && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            holdRequests.countDown();

            long unavailable = 0;
            for (Future<ProductWithInventory> call : calls) {
                if (call.get(5, TimeUnit.SECONDS).inventoryStatus().equals("unavailable")) {
                    unavailable++;
                }
            }
            assertThat(bulkhead.rejected()).isEqualTo(5);
            assertThat(unavailable).isEqualTo(5);
            assertThat(inventoryCalls.get()).isEqualTo(10);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void rejectsInvalidAndUnknownProductIds() throws Exception {
        for (String bad : new String[] {"abc", "0", "-5", "1.5"}) {
            mvc.perform(get("/products/" + bad))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().json("{\"error\":\"INVALID_INPUT\",\"message\":\"Invalid product ID\"}", true));
        }
        mvc.perform(get("/products/999999"))
                .andExpect(status().isNotFound())
                .andExpect(content().json("{\"error\":\"NOT_FOUND\",\"message\":\"No product found with ID 999999\"}",
                        true));
    }

    @Test
    void addingDetailsReplacesTheStoredProduct() throws Exception {
        mvc.perform(post("/products/5/details").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"SKU-NEW\",\"manufacturer\":\"Acme\",\"name\":\"Widget\",\"weight\":12,"
                                + "\"id\":777,\"product_id\":5}"))
                .andExpect(status().isNoContent());

        mvc.perform(get("/products/5")).andExpect(content().json("{\"id\":5,\"name\":\"Widget\",\"category\":\"\","
                + "\"description\":\"\",\"brand\":\"\",\"sku\":\"SKU-NEW\",\"manufacturer\":\"Acme\",\"category_id\":0,"
                + "\"weight\":12,\"some_other_id\":0,\"stock\":127,\"warehouse\":\"EU-Central\","
                + "\"inventory_status\":\"in_stock\"}", true));
    }

    @Test
    void validatesProductDetails() throws Exception {
        expectDetailsError("1", "{\"manufacturer\":\"Acme\"}", "SKU is required");
        expectDetailsError("1", "{\"sku\":\"S\",\"manufacturer\":\"\"}", "Manufacturer is required");
        expectDetailsError("x", "{\"sku\":\"S\",\"manufacturer\":\"Acme\"}", "Invalid product ID");

        mvc.perform(post("/products/1/details").contentType(MediaType.APPLICATION_JSON).content("{\"weight\":\"heavy\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("Invalid input data"))
                .andExpect(jsonPath("$.details").isNotEmpty());
        mvc.perform(post("/products/1/details").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details").value("EOF"));
    }

    @Test
    void searchMatchesNameOrCategoryInTheFirst101Products() throws Exception {
        mvc.perform(get("/products/search"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(
                        "{\"error\":\"INVALID_INPUT\",\"message\":\"Query parameter 'q' is required\"}", true));
        mvc.perform(get("/products/search?q=")).andExpect(status().isBadRequest());

        mvc.perform(get("/products/search?q=alpha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_found").value(12))
                .andExpect(jsonPath("$.products.length()").value(12))
                .andExpect(jsonPath("$.products[0].id").value(8))
                .andExpect(jsonPath("$.products[0].stock").value(178))
                .andExpect(jsonPath("$.search_time").isNotEmpty());
        mvc.perform(get("/products/search?q=BOOKS")).andExpect(jsonPath("$.total_found").value(13));
        mvc.perform(get("/products/search?q=product"))
                .andExpect(jsonPath("$.total_found").value(101))
                .andExpect(jsonPath("$.products.length()").value(20));
        mvc.perform(get("/products/search?q=a,b")).andExpect(jsonPath("$.total_found").value(0));
    }

    @Test
    void healthAndInitialMetrics() throws Exception {
        mvc.perform(get("/health")).andExpect(content().json("{\"status\":\"healthy\"}", true));
        mvc.perform(get("/metrics")).andExpect(content().json(
                "{\"circuit_breaker\":{\"state\":\"CLOSED\",\"total_requests\":0,\"successes\":0,\"failures\":0,"
                        + "\"rejected_by_circuit\":0},\"bulkhead\":{\"max_concurrent\":10,\"rejected\":0}}", true));
    }

    @Test
    void proxiesInventoryModeSwitches() throws Exception {
        mvc.perform(post("/inventory/mode/crashed"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"message\":\"Mode changed to crashed\",\"previous\":\"healthy\"}", true));
        mvc.perform(post("/inventory/mode/sleepy")).andExpect(status().isNotFound());

        inventoryStub.stop(0);
        mvc.perform(post("/inventory/mode/healthy"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    private void expectDetailsError(String id, String body, String message) throws Exception {
        mvc.perform(post("/products/" + id + "/details").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"error\":\"INVALID_INPUT\",\"message\":\"" + message + "\"}", true));
    }

    private void serveInventory(HttpExchange exchange) throws IOException {
        inventoryCalls.incrementAndGet();
        try {
            if (holdRequests != null) {
                holdRequests.await(10, TimeUnit.SECONDS);
            }
            if (stubDelayMillis > 0) {
                Thread.sleep(stubDelayMillis);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (stubStatus != 200) {
            respond(exchange, stubStatus, "{\"error\":\"INTERNAL_ERROR\"}");
            return;
        }
        long id = Long.parseLong(exchange.getRequestURI().getPath().substring("/inventory/".length()));
        String warehouse = new String[] {"US-East", "US-West", "EU-Central"}[(int) (id % 3)];
        respond(exchange, 200, "{\"product_id\":" + id + ",\"stock\":" + (id * 17 + 42) % 500
                + ",\"warehouse\":\"" + warehouse + "\",\"status\":\"in_stock\"}");
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        try {
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        } catch (IOException clientGaveUp) {
            // the product API cancelled the call after its deadline
        } finally {
            exchange.close();
        }
    }
}
