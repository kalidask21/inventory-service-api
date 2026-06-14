package com.nuuly.inventory.api;

import com.nuuly.inventory.api.dto.InventoryItemResponse;
import com.nuuly.inventory.api.dto.InventoryQuantityRequest;
import com.nuuly.inventory.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService service;

    public InventoryController(InventoryService service) {
        this.service = service;
    }

    @GetMapping("/{skuId}")
    public InventoryItemResponse get(@PathVariable String skuId) {
        return InventoryItemResponse.from(service.get(skuId));
    }

    @PostMapping("/{skuId}")
    public InventoryItemResponse add(@PathVariable String skuId,
                                     @Valid @RequestBody InventoryQuantityRequest req) {
        return InventoryItemResponse.from(service.addStock(skuId, req.quantity()));
    }

    @PostMapping("/{skuId}/purchase")
    public InventoryItemResponse purchase(@PathVariable String skuId,
                                          @Valid @RequestBody InventoryQuantityRequest req) {
        return InventoryItemResponse.from(service.purchase(skuId, req.quantity()));
    }

    @GetMapping
    public List<InventoryItemResponse> list() {
        return service.listAll().stream().map(InventoryItemResponse::from).toList();
    }
}
