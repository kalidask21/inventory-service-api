package com.nuuly.inventory.domain;

public class InsufficientInventoryException extends RuntimeException {
    public InsufficientInventoryException() {
        super("Insufficient inventory");
    }
}
