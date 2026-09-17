package edu.cit.Abesia.inventory;

import java.util.List;
import java.util.Optional;

public interface InventoryService {

    Optional<Inventory> getItem(String productId);

    List<Inventory> getAllItems();

    boolean reserve(String productId, int quantity);

    void restock(String productId, int quantity);
}