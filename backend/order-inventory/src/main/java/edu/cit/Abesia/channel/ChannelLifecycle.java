package edu.cit.Abesia.channel;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.cit.Abesia.AppInstance;
import edu.cit.Abesia.inventory.Inventory;
import edu.cit.Abesia.inventory.InventoryService;
import edu.cit.Abesia.supplier.SupplierGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Goes live on start (heartbeat, listings, current stock) and keeps the heartbeat going every 30 seconds. */
@Component
class ChannelLifecycle {

    private static final Logger log = LoggerFactory.getLogger(ChannelLifecycle.class);

    private final TianggeClient client;
    private final InventoryService inventory;
    private final SupplierGateway supplier;
    private final StockSync stockSync;
    private volatile boolean live;

    ChannelLifecycle(TianggeClient client, InventoryService inventory, SupplierGateway supplier, StockSync stockSync) {
        this.client = client;
        this.inventory = inventory;
        this.supplier = supplier;
        this.stockSync = stockSync;
    }

    boolean isLive() { return live; }

    @EventListener(ApplicationReadyEvent.class)
    void onReady() {
        log.info("Tiangge channel starting, instance id {}", AppInstance.ID);
        goLive();
    }

    /** Keeps trying until Tiangge accepts us (it may be down at startup). */
    @Scheduled(fixedDelay = 5000, initialDelay = 5000)
    void ensureLive() {
        if (!live) {
            goLive();
        }
    }

    @Scheduled(fixedRate = 30000, initialDelay = 30000)
    void heartbeat() {
        if (!live) {
            return;
        }
        try {
            client.heartbeat();
        } catch (Exception e) {
            log.warn("Heartbeat failed: {}", e.getMessage());
        }
    }

    private synchronized void goLive() {
        if (live) {
            return;
        }
        try {
            client.heartbeat();                 // first call, before anything else
            client.putListings(listings());
            live = true;
            stockSync.setLive(true);
            stockSync.publishAll();
            log.info("Tiangge shop is live (instance {})", AppInstance.ID);
        } catch (Exception e) {
            log.warn("Could not go live yet: {}", e.getMessage());
        }
    }

    private ArrayNode listings() {
        ArrayNode listings = TianggeClient.array();
        for (Inventory item : inventory.getAllItems()) {
            Optional<String> sku = supplier.supplierSkuFor(item.getProductId());
            if (sku.isPresent() && listings.size() < 10) {
                ObjectNode l = TianggeClient.object();
                l.put("sellerSku", item.getProductId());
                l.put("title", item.getName());
                l.put("supplierSku", sku.get());
                listings.add(l);
            }
        }
        return listings;
    }
}
