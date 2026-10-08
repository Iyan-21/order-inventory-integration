package edu.cit.Abesia.channel;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** Durable channel state: feed cursor, processed event ids, and one row per Tiangge order. */
@Component
class ChannelStore {

    record ChannelOrderRow(String tianggeOrderId, int shopOrderId, String decision, String reason,
                           boolean decisionSent, String resolution, boolean resolutionSent,
                           boolean cancelApplied, boolean cancelConfirmed, String linesJson) {}

    private static final RowMapper<ChannelOrderRow> MAPPER = (rs, i) -> new ChannelOrderRow(
            rs.getString("tiangge_order_id"), rs.getInt("shop_order_id"), rs.getString("decision"),
            rs.getString("decision_reason"), rs.getBoolean("decision_sent"), rs.getString("resolution"),
            rs.getBoolean("resolution_sent"), rs.getBoolean("cancel_applied"),
            rs.getBoolean("cancel_confirmed"), rs.getString("lines_json"));

    private final JdbcTemplate jdbc;

    ChannelStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        jdbc.execute("CREATE TABLE IF NOT EXISTS channel_state ("
                + "state_key VARCHAR(50) PRIMARY KEY, state_value VARCHAR(100) NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS channel_event ("
                + "event_id VARCHAR(100) PRIMARY KEY, processed_at TIMESTAMP NOT NULL DEFAULT NOW())");
        jdbc.execute("CREATE TABLE IF NOT EXISTS channel_order ("
                + "tiangge_order_id VARCHAR(100) PRIMARY KEY, shop_order_id INTEGER NOT NULL, "
                + "decision VARCHAR(20) NOT NULL, decision_reason VARCHAR(255), "
                + "decision_sent BOOLEAN NOT NULL DEFAULT FALSE, resolution VARCHAR(20), "
                + "resolution_sent BOOLEAN NOT NULL DEFAULT FALSE, cancel_applied BOOLEAN NOT NULL DEFAULT FALSE, "
                + "cancel_confirmed BOOLEAN NOT NULL DEFAULT FALSE, lines_json TEXT NOT NULL, "
                + "created_at TIMESTAMP NOT NULL DEFAULT NOW())");
    }

    String cursor() {
        List<String> r = jdbc.queryForList("SELECT state_value FROM channel_state WHERE state_key = 'feed_cursor'", String.class);
        return r.isEmpty() ? "0" : r.get(0);
    }

    void setCursor(String cursor) {
        jdbc.update("INSERT INTO channel_state (state_key, state_value) VALUES ('feed_cursor', ?) "
                + "ON CONFLICT (state_key) DO UPDATE SET state_value = EXCLUDED.state_value", cursor);
    }

    boolean eventDone(String eventId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM channel_event WHERE event_id = ?", Integer.class, eventId);
        return n != null && n > 0;
    }

    void markEventDone(String eventId) {
        jdbc.update("INSERT INTO channel_event (event_id) VALUES (?) ON CONFLICT (event_id) DO NOTHING", eventId);
    }

    Optional<ChannelOrderRow> find(String tianggeOrderId) {
        return jdbc.query("SELECT * FROM channel_order WHERE tiangge_order_id = ?", MAPPER, tianggeOrderId)
                .stream().findFirst();
    }

    void insertOrder(String tianggeOrderId, int shopOrderId, String decision, String reason, String linesJson) {
        jdbc.update("INSERT INTO channel_order (tiangge_order_id, shop_order_id, decision, decision_reason, lines_json) "
                + "VALUES (?, ?, ?, ?, ?)", tianggeOrderId, shopOrderId, decision, reason, linesJson);
    }

    void markDecisionSent(String id) {
        jdbc.update("UPDATE channel_order SET decision_sent = TRUE WHERE tiangge_order_id = ?", id);
    }

    void setResolution(String id, String resolution) {
        jdbc.update("UPDATE channel_order SET resolution = ?, resolution_sent = FALSE WHERE tiangge_order_id = ?", resolution, id);
    }

    void markResolutionSent(String id) {
        jdbc.update("UPDATE channel_order SET resolution_sent = TRUE WHERE tiangge_order_id = ?", id);
    }

    void markCancelApplied(String id) {
        jdbc.update("UPDATE channel_order SET cancel_applied = TRUE WHERE tiangge_order_id = ?", id);
    }

    void markCancelConfirmed(String id) {
        jdbc.update("UPDATE channel_order SET cancel_confirmed = TRUE WHERE tiangge_order_id = ?", id);
    }

    List<ChannelOrderRow> pendingBackorders() {
        return jdbc.query("SELECT * FROM channel_order WHERE decision = 'BACKORDERED' AND resolution IS NULL "
                + "AND cancel_applied = FALSE ORDER BY created_at, tiangge_order_id", MAPPER);
    }

    List<ChannelOrderRow> unsentResolutions() {
        return jdbc.query("SELECT * FROM channel_order WHERE resolution IS NOT NULL AND resolution_sent = FALSE "
                + "ORDER BY created_at", MAPPER);
    }
}
