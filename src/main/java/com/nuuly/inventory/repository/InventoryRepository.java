package com.nuuly.inventory.repository;

import com.nuuly.inventory.domain.InventoryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryRepository extends JpaRepository<InventoryItem, String> {

    /**
     * Atomically decrement stock only if sufficient.
     * @return rows affected: 1 on success, 0 if SKU missing OR insufficient stock.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE InventoryItem i SET i.quantity = i.quantity - :qty " +
           "WHERE i.skuId = :skuId AND i.quantity >= :qty")
    int decrementIfSufficient(@Param("skuId") String skuId, @Param("qty") int qty);
}
