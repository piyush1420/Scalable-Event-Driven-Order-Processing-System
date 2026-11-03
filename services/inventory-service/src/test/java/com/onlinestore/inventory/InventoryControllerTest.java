package com.onlinestore.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class InventoryControllerTest {

    private final List<Duration> sleeps = new ArrayList<>();
    private final FixedRandom random = new FixedRandom();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        InventoryController controller = new InventoryController(new InventoryState(), sleeps::add, () -> random);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void healthyModeReturnsDeterministicStockWithoutDelay() throws Exception {
        mvc.perform(get("/inventory/1"))
                .andExpect(status().isOk())
                .andExpect(content().json(
                        "{\"product_id\":1,\"stock\":59,\"warehouse\":\"US-West\",\"status\":\"in_stock\"}", true));
        mvc.perform(get("/inventory/5"))
                .andExpect(content().json("{\"product_id\":5,\"stock\":127,\"warehouse\":\"EU-Central\"}"));
        mvc.perform(get("/inventory/1000"))
                .andExpect(content().json("{\"product_id\":1000,\"stock\":42,\"warehouse\":\"US-West\"}"));

        assertThat(sleeps).isEmpty();
        mvc.perform(get("/stats")).andExpect(content().json(
                "{\"mode\":\"healthy\",\"total_reqs\":3,\"failed_reqs\":0,\"success_reqs\":3,\"fail_rate\":\"0.0%\"}",
                true));
    }

    @Test
    void invalidIdIsRejectedButStillCounted() throws Exception {
        mvc.perform(get("/inventory/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"error\":\"invalid product ID\"}", true));
        mvc.perform(get("/inventory/1.5")).andExpect(status().isBadRequest());

        mvc.perform(get("/stats")).andExpect(content().json(
                "{\"total_reqs\":2,\"failed_reqs\":0,\"success_reqs\":0}"));
    }

    @Test
    void crashedModeHangsTenSecondsThenFails() throws Exception {
        mvc.perform(post("/mode/crashed"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"message\":\"Mode changed to crashed\",\"previous\":\"healthy\"}", true));

        mvc.perform(get("/inventory/1"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().json(
                        "{\"error\":\"SERVICE_UNAVAILABLE\",\"message\":\"Inventory service is down\"}", true));

        assertThat(sleeps).containsExactly(Duration.ofSeconds(10));
        mvc.perform(get("/stats")).andExpect(content().json(
                "{\"mode\":\"crashed\",\"total_reqs\":1,\"failed_reqs\":1,\"success_reqs\":0,\"fail_rate\":\"100.0%\"}",
                true));
    }

    @Test
    void degradedModeFailureWaitsThreeToTenSeconds() throws Exception {
        mvc.perform(post("/mode/degraded")).andExpect(status().isOk());
        random.nextDouble = 0.3; // below 0.5 -> failure path
        random.nextInt = 2;      // 3 + 2 = 5 seconds

        mvc.perform(get("/inventory/7"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().json(
                        "{\"error\":\"INTERNAL_ERROR\",\"message\":\"Inventory database timeout\"}", true));

        assertThat(sleeps).containsExactly(Duration.ofSeconds(5));
        assertThat(random.bounds).containsExactly(8);
    }

    @Test
    void degradedModeSuccessIsSlowButReturnsStock() throws Exception {
        mvc.perform(post("/mode/degraded")).andExpect(status().isOk());
        random.nextDouble = 0.7; // 0.5 or above -> slow success path
        random.nextInt = 1;      // 2 + 1 = 3 seconds

        mvc.perform(get("/inventory/2"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"product_id\":2,\"stock\":76,\"warehouse\":\"EU-Central\"}"));

        assertThat(sleeps).containsExactly(Duration.ofSeconds(3));
        assertThat(random.bounds).containsExactly(3);
    }

    @Test
    void changingModeResetsCountersAndReportsPreviousMode() throws Exception {
        mvc.perform(get("/inventory/1"));
        mvc.perform(post("/mode/degraded"));
        mvc.perform(post("/mode/healthy"))
                .andExpect(content().json("{\"message\":\"Mode changed to healthy\",\"previous\":\"degraded\"}", true));

        mvc.perform(get("/stats")).andExpect(content().json("{\"total_reqs\":0,\"success_reqs\":0}"));
        mvc.perform(post("/mode/broken")).andExpect(status().isNotFound());
    }

    @Test
    void failRateUsesOneDecimalWithTiesToEven() {
        assertThat(InventoryController.formatFailRate(0, 0)).isEqualTo("0.0%");
        assertThat(InventoryController.formatFailRate(1, 3)).isEqualTo("33.3%");
        assertThat(InventoryController.formatFailRate(1, 6)).isEqualTo("16.7%");
        assertThat(InventoryController.formatFailRate(3, 8)).isEqualTo("37.5%");
        assertThat(InventoryController.formatFailRate(1, 400)).isEqualTo("0.2%"); // exactly 0.25 -> 0.2
    }

    /** Random source whose answers are chosen by the test. */
    private static final class FixedRandom implements RandomGenerator {
        double nextDouble;
        int nextInt;
        final List<Integer> bounds = new ArrayList<>();

        @Override
        public long nextLong() {
            return 0;
        }

        @Override
        public double nextDouble() {
            return nextDouble;
        }

        @Override
        public int nextInt(int bound) {
            bounds.add(bound);
            return nextInt;
        }
    }
}
