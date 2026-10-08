package edu.cit.Abesia.channel;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Checks the Tiangge order feed by itself every couple of seconds and remembers where it stopped. */
@Component
class OrderFeedPoller {

    private static final Logger log = LoggerFactory.getLogger(OrderFeedPoller.class);
    private static final int PAGE = 50;

    private final TianggeClient client;
    private final ChannelStore store;
    private final ChannelOrderProcessor processor;
    private final ChannelLifecycle lifecycle;

    OrderFeedPoller(TianggeClient client, ChannelStore store, ChannelOrderProcessor processor, ChannelLifecycle lifecycle) {
        this.client = client;
        this.store = store;
        this.processor = processor;
        this.lifecycle = lifecycle;
    }

    @Scheduled(fixedDelay = 2000, initialDelay = 5000)
    void poll() {
        if (!lifecycle.isLive()) {
            return;
        }
        try {
            for (int page = 0; page < 20; page++) {            // drain a backlog (flash sale, restart catch-up)
                JsonNode res = client.feed(store.cursor(), PAGE);
                JsonNode events = res.path("events");
                if (!events.isArray() || events.size() == 0) {
                    return;
                }
                for (JsonNode event : events) {
                    processor.handle(event);                  // throws on failure: cursor stays before this event
                    store.setCursor(event.path("seq").asText());
                }
                String next = res.path("nextCursor").asText("");
                if (!next.isBlank() && !"null".equals(next)) {
                    store.setCursor(next);
                }
                if (events.size() < PAGE) {
                    return;
                }
            }
        } catch (Exception e) {
            log.warn("Feed poll stopped, will retry: {}", e.getMessage());
        }
    }
}
