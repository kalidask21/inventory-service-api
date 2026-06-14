package com.nuuly.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "inventory")
public class InventoryItem {

    @Id
    @Column(nullable = false, updatable = false)
    private String skuId;

    @Column(nullable = false)
    private int quantity;

    protected InventoryItem() {}

    public InventoryItem(String skuId, int quantity) {
        this.skuId = skuId;
        this.quantity = quantity;
    }

    public String getSkuId() {
        return skuId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}
