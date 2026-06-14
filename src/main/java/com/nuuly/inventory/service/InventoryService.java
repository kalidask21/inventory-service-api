package com.nuuly.inventory.service;

import com.nuuly.inventory.domain.InventoryItem;
import com.nuuly.inventory.domain.InsufficientInventoryException;
import com.nuuly.inventory.domain.SkuNotFoundException;
import com.nuuly.inventory.repository.InventoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final InventoryRepository repo;

    public InventoryService(InventoryRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public InventoryItem get(String skuId) {
        log.debug("GET sku={}", skuId);
        return repo.findById(skuId)
                   .orElseThrow(() -> {
                       log.warn("GET failed — sku={} not found", skuId);
                       return new SkuNotFoundException(skuId);
                   });
    }

    @Transactional(readOnly = true)
    public List<InventoryItem> listAll() {
        List<InventoryItem> items = repo.findAll();
        log.debug("LIST all → {} skus", items.size());
        return items;
    }

    @Transactional
    public InventoryItem addStock(String skuId, int quantity) {
        log.debug("ADD_STOCK sku={} qty={}", skuId, quantity);
        boolean isNew = !repo.existsById(skuId);
        InventoryItem item = repo.findById(skuId)
                                 .orElseGet(() -> new InventoryItem(skuId, 0));
        item.setQuantity(item.getQuantity() + quantity);
        InventoryItem saved = repo.save(item);
        log.info("ADD_STOCK {} sku={} qty=+{} total={}", isNew ? "CREATED" : "UPDATED",
                skuId, quantity, saved.getQuantity());
        return saved;
    }

    @Transactional
    public InventoryItem purchase(String skuId, int quantity) {
        log.debug("PURCHASE sku={} qty={}", skuId, quantity);
        int rows = repo.decrementIfSufficient(skuId, quantity);
        if (rows == 1) {
            InventoryItem result = repo.findById(skuId)
                    .orElseThrow(() -> new SkuNotFoundException(skuId));
            log.info("PURCHASE SUCCESS sku={} qty={} remaining={}", skuId, quantity, result.getQuantity());
            return result;
        }
        // rows == 0: distinguish missing SKU from insufficient stock
        if (repo.existsById(skuId)) {
            log.warn("PURCHASE FAILED sku={} qty={} reason=INSUFFICIENT_STOCK", skuId, quantity);
            throw new InsufficientInventoryException();
        }
        log.warn("PURCHASE FAILED sku={} qty={} reason=SKU_NOT_FOUND", skuId, quantity);
        throw new SkuNotFoundException(skuId);
    }
}
