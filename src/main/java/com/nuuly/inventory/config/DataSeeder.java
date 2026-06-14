package com.nuuly.inventory.config;

import com.nuuly.inventory.domain.InventoryItem;
import com.nuuly.inventory.repository.InventoryRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Component
@Profile("!test")
public class DataSeeder implements CommandLineRunner {

    private final InventoryRepository repo;

    public DataSeeder(InventoryRepository repo) {
        this.repo = repo;
    }

    @Override
    public void run(String... args) {
        if (repo.count() > 0) return;

        Random rnd = new Random(42);
        List<InventoryItem> items = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            String sku = "CW-%04d-BM-%02d".formatted(i, (i % 12) + 1);
            int qty = 10 + rnd.nextInt(491);
            items.add(new InventoryItem(sku, qty));
        }
        repo.saveAll(items);
    }
}
