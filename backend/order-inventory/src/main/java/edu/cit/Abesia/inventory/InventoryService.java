package edu.cit.Abesia.inventory;

import java.util.Optional;

public interface InventoryService {

    Optional<Inventory> getItem(String productId);

    /**
     * Attempts to reserve (deduct) the given quantity from the product's stock.
     * @return true if the reservation succeeded, false if there wasn't enough stock
     *         or the product doesn't exist.
     */
    boolean reserve(String productId, int quantity);
}