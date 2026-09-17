package edu.cit.Abesia.inventory;

import edu.cit.Abesia.events.LowStockEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
class InventoryServiceImpl implements InventoryService {

    private static final int LOW_STOCK_THRESHOLD = 5;

    private final InventoryRepository inventoryRepository;
    private final ApplicationEventPublisher eventPublisher;

    InventoryServiceImpl(InventoryRepository inventoryRepository, ApplicationEventPublisher eventPublisher) {
        this.inventoryRepository = inventoryRepository;
        this.eventPublisher = eventPublisher;
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
            return false;
        }

        int newStock = item.getStock() - quantity;
        item.setStock(newStock);
        inventoryRepository.save(item);

        if (newStock < LOW_STOCK_THRESHOLD) {
            eventPublisher.publishEvent(new LowStockEvent(productId, item.getName(), newStock));
        }

        return true;
    }

    @Override
    @Transactional
    public void restock(String productId, int quantity) {
        inventoryRepository.findById(productId).ifPresent(item -> {
            item.setStock(item.getStock() + quantity);
            inventoryRepository.save(item);
        });
    }
    @Override
    public List<Inventory> getAllItems() {
        return inventoryRepository.findAll();
    }

}