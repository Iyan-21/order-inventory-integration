package edu.cit.Abesia.inventory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

// No "public" modifier -> package-private. The shop/Order module cannot see
// this class directly; it can only depend on the InventoryService interface,
// which is public. This is the enforced module boundary the assignment asks for.
@Service
class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;

    InventoryServiceImpl(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Override
    public Optional<Inventory> getItem(String productId) {
        return inventoryRepository.findById(productId);
    }

    @Override
    @Transactional
    public boolean reserve(String productId, int quantity) {
        Optional<Inventory> itemOpt = inventoryRepository.findById(productId);

        if (itemOpt.isEmpty()) {
            return false;
        }

        Inventory item = itemOpt.get();

        if (quantity > item.getStock()) {
            return false; // not enough stock -> reject
        }

        item.setStock(item.getStock() - quantity);
        inventoryRepository.save(item);
        return true;
    }
}