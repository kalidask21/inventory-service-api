package com.nuuly.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuuly.inventory.domain.InventoryItem;
import com.nuuly.inventory.repository.InventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InventoryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InventoryRepository repo;

    @Autowired
    private ObjectMapper objectMapper;

    private String bearerToken;

    @BeforeEach
    void setup() throws Exception {
        repo.deleteAll();
        bearerToken = fetchToken();
    }

    // -------------------------------------------------------------------------
    // Token helpers
    // -------------------------------------------------------------------------

    private String fetchToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("grant_type=client_credentials&scope=inventory.read inventory.write")
                        .header("Authorization", basicAuth("inventory-client", "inventory-secret")))
                .andExpect(status().isOk())
                .andReturn();
        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return (String) body.get("access_token");
    }

    private String basicAuth(String user, String pass) {
        String creds = user + ":" + pass;
        return "Basic " + java.util.Base64.getEncoder().encodeToString(creds.getBytes());
    }

    private String json(Map<String, ?> map) throws Exception {
        return objectMapper.writeValueAsString(map);
    }

    // -------------------------------------------------------------------------
    // Auth tests (FR security)
    // -------------------------------------------------------------------------

    @Test
    void getInventory_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/inventory"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getInventory_withReadScopeToken_returns200() throws Exception {
        mockMvc.perform(get("/inventory")
                        .header("Authorization", "Bearer " + bearerToken))
                .andExpect(status().isOk());
    }

    // -------------------------------------------------------------------------
    // FR-1: GET /inventory/{skuId}
    // -------------------------------------------------------------------------

    @Test
    void getInventory_existingSku_returns200() throws Exception {
        repo.save(new InventoryItem("widget", 50));

        mockMvc.perform(get("/inventory/widget")
                        .header("Authorization", "Bearer " + bearerToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.skuId").value("widget"))
                .andExpect(jsonPath("$.quantity").value(50));
    }

    @Test
    void getInventory_missingSku_returns404PlainText() throws Exception {
        mockMvc.perform(get("/inventory/nonexistent")
                        .header("Authorization", "Bearer " + bearerToken))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN));
    }

    // -------------------------------------------------------------------------
    // FR-2: POST /inventory/{skuId} — additive upsert
    // -------------------------------------------------------------------------

    @Test
    void addStock_newSku_creates() throws Exception {
        mockMvc.perform(post("/inventory/new-sku")
                        .header("Authorization", "Bearer " + bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("quantity", 10))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skuId").value("new-sku"))
                .andExpect(jsonPath("$.quantity").value(10));
    }

    @Test
    void addStock_existingSku_addsQuantity() throws Exception {
        repo.save(new InventoryItem("widget", 30));

        mockMvc.perform(post("/inventory/widget")
                        .header("Authorization", "Bearer " + bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("quantity", 20))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(50));
    }

    @Test
    void addStock_invalidQuantity_returns400PlainText() throws Exception {
        mockMvc.perform(post("/inventory/widget")
                        .header("Authorization", "Bearer " + bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("quantity", 0))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN));
    }

    @Test
    void addStock_missingQuantity_returns400PlainText() throws Exception {
        mockMvc.perform(post("/inventory/widget")
                        .header("Authorization", "Bearer " + bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN));
    }

    @Test
    void addStock_malformedJson_returns400PlainText() throws Exception {
        mockMvc.perform(post("/inventory/widget")
                        .header("Authorization", "Bearer " + bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN));
    }

    // -------------------------------------------------------------------------
    // FR-3: POST /inventory/{skuId}/purchase
    // -------------------------------------------------------------------------

    @Test
    void purchase_sufficientStock_decrements() throws Exception {
        repo.save(new InventoryItem("widget", 20));

        mockMvc.perform(post("/inventory/widget/purchase")
                        .header("Authorization", "Bearer " + bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("quantity", 5))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(15));
    }

    @Test
    void purchase_insufficientStock_returns400WithExactMessage() throws Exception {
        repo.save(new InventoryItem("widget", 3));

        mockMvc.perform(post("/inventory/widget/purchase")
                        .header("Authorization", "Bearer " + bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("quantity", 5))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("Insufficient inventory"));
    }

    @Test
    void purchase_missingSku_returns404PlainText() throws Exception {
        mockMvc.perform(post("/inventory/ghost/purchase")
                        .header("Authorization", "Bearer " + bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("quantity", 1))))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN));
    }

    @Test
    void purchase_invalidQuantity_returns400PlainText() throws Exception {
        mockMvc.perform(post("/inventory/widget/purchase")
                        .header("Authorization", "Bearer " + bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("quantity", -1))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN));
    }

    // -------------------------------------------------------------------------
    // FR-4: GET /inventory
    // -------------------------------------------------------------------------

    @Test
    void listInventory_empty_returnsEmptyArray() throws Exception {
        mockMvc.perform(get("/inventory")
                        .header("Authorization", "Bearer " + bearerToken))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void listInventory_populated_returnsAll() throws Exception {
        repo.save(new InventoryItem("a", 10));
        repo.save(new InventoryItem("b", 20));

        mockMvc.perform(get("/inventory")
                        .header("Authorization", "Bearer " + bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    // -------------------------------------------------------------------------
    // NFR-1: Concurrency — no oversell
    // -------------------------------------------------------------------------

    @Test
    void purchase_concurrent_noOversell() throws Exception {
        int initialQty = 10;
        int purchaseQty = 1;
        int threads = 20;
        repo.save(new InventoryItem("concurrent-sku", initialQty));

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                try {
                    latch.await();
                    MvcResult result = mockMvc.perform(post("/inventory/concurrent-sku/purchase")
                                    .header("Authorization", "Bearer " + bearerToken)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"quantity\":" + purchaseQty + "}"))
                            .andReturn();
                    if (result.getResponse().getStatus() == 200) {
                        successes.incrementAndGet();
                    } else {
                        failures.incrementAndGet();
                    }
                } catch (Exception e) {
                    failures.incrementAndGet();
                }
            }));
        }

        latch.countDown();
        for (Future<?> f : futures) f.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(successes.get()).isEqualTo(initialQty);
        assertThat(successes.get() + failures.get()).isEqualTo(threads);

        InventoryItem remaining = repo.findById("concurrent-sku").orElseThrow();
        assertThat(remaining.getQuantity()).isZero();
    }
}
