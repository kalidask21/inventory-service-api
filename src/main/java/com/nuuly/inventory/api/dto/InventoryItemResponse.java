package com.nuuly.inventory.api.dto;

import com.nuuly.inventory.domain.InventoryItem;

public record InventoryItemResponse(String skuId, int quantity) {
    public static InventoryItemResponse from(InventoryItem item) {
        return new InventoryItemResponse(item.getSkuId(), item.getQuantity());
    }
}
