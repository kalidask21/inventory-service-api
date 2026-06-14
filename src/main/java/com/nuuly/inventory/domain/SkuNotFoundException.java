package com.nuuly.inventory.domain;

public class SkuNotFoundException extends RuntimeException {
    public SkuNotFoundException(String skuId) {
        super("SKU not found: " + skuId);
    }
}
