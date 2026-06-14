package com.nuuly.inventory.service;

import com.nuuly.inventory.domain.InventoryItem;
import com.nuuly.inventory.domain.InsufficientInventoryException;
import com.nuuly.inventory.domain.SkuNotFoundException;
import com.nuuly.inventory.repository.InventoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class InventoryService {

    private final InventoryRepository repo;

    public InventoryService(InventoryRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public InventoryItem get(String skuId) {
        return repo.findById(skuId)
                   .orElseThrow(() -> new SkuNotFoundException(skuId));
    }

    @Transactional(readOnly = true)
    public List<InventoryItem> listAll() {
        return repo.findAll();
    }

    @Transactional
    public InventoryItem addStock(String skuId, int quantity) {
        InventoryItem item = repo.findById(skuId)
                                 .orElseGet(() -> new InventoryItem(skuId, 0));
        item.setQuantity(item.getQuantity() + quantity);
        return repo.save(item);
    }

    @Transactional
    public InventoryItem purchase(String skuId, int quantity) {
        int rows = repo.decrementIfSufficient(skuId, quantity);
        if (rows == 1) {
            return repo.findById(skuId).orElseThrow(() -> new SkuNotFoundException(skuId));
        }
        // rows == 0: distinguish missing SKU from insufficient stock
        if (repo.existsById(skuId)) {
            throw new InsufficientInventoryException();
        }
        throw new SkuNotFoundException(skuId);
    }
}
