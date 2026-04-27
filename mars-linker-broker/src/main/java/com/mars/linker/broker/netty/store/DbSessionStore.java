package com.mars.linker.broker.netty.store;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DB 版 SessionStore（全量快照语义）。
 */
public final class DbSessionStore implements SessionStore {
    private static final Logger log = LoggerFactory.getLogger(DbSessionStore.class);

    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final String tableSubs;
    private final String tableOffline;
    private final String tableSessionIndex;

    public DbSessionStore(String jdbcUrl, String username, String password, String schema, String tablePrefix) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        String fullPrefix = DbStoreNaming.prefix(schema, tablePrefix);
        this.tableSubs = fullPrefix + "session_subscriptions";
        this.tableOffline = fullPrefix + "session_offline_messages";
        this.tableSessionIndex = fullPrefix + "sessions";
        initSchema();
    }

    @Override
    public synchronized Map<String, SessionService.Session> loadAll() {
        Map<String, SessionService.Session> out = new ConcurrentHashMap<>();
        try (Connection c = getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT client_id FROM " + tableSessionIndex);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String clientId = rs.getString(1);
                    if (clientId != null && !clientId.isEmpty()) {
                        out.put(clientId, new SessionService.Session(clientId));
                    }
                }
            }

            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT client_id, topic_filter, qos FROM " + tableSubs);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String clientId = rs.getString(1);
                    SessionService.Session s = out.computeIfAbsent(clientId, SessionService.Session::new);
                    s.subscriptionsQos.put(rs.getString(2), rs.getInt(3));
                }
            }

            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT client_id, topic, payload, retain_flag, qos, created_at FROM " + tableOffline
                            + " ORDER BY client_id, msg_order");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String clientId = rs.getString(1);
                    SessionService.Session s = out.computeIfAbsent(clientId, SessionService.Session::new);
                    s.offlineQueue.add(new SessionService.QueuedMessage(
                            rs.getString(2),
                            rs.getBytes(3),
                            rs.getInt(4) == 1,
                            rs.getInt(5),
                            rs.getLong(6) > 0 ? rs.getLong(6) : System.currentTimeMillis()
                    ));
                }
            }
        } catch (SQLException e) {
            log.warn("DbSessionStore loadAll 失败", e);
        }
        return out;
    }

    @Override
    public synchronized void persistAll(Map<String, SessionService.Session> sessions) {
        try (Connection c = getConnection()) {
            c.setAutoCommit(false);
            try {
                clearTables(c);
                insertAll(c, sessions);
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            log.warn("DbSessionStore persistAll 失败", e);
        }
    }

    @Override
    public synchronized void deleteIfExists() {
        try (Connection c = getConnection()) {
            clearTables(c);
        } catch (SQLException e) {
            log.warn("DbSessionStore deleteIfExists 失败", e);
        }
    }

    private void insertAll(Connection c, Map<String, SessionService.Session> sessions) throws SQLException {
        try (PreparedStatement psIndex = c.prepareStatement("INSERT INTO " + tableSessionIndex + "(client_id) VALUES (?)");
             PreparedStatement psSub = c.prepareStatement(
                     "INSERT INTO " + tableSubs + "(client_id, topic_filter, qos) VALUES (?, ?, ?)");
             PreparedStatement psOffline = c.prepareStatement(
                     "INSERT INTO " + tableOffline + "(client_id, msg_order, topic, payload, retain_flag, qos, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            for (SessionService.Session s : sessions.values()) {
                if (s == null || s.clientId == null) {
                    continue;
                }
                psIndex.setString(1, s.clientId);
                psIndex.addBatch();

                for (Map.Entry<String, Integer> sub : s.subscriptionsQos.entrySet()) {
                    psSub.setString(1, s.clientId);
                    psSub.setString(2, sub.getKey());
                    psSub.setInt(3, sub.getValue() == null ? 0 : sub.getValue());
                    psSub.addBatch();
                }

                int order = 0;
                for (SessionService.QueuedMessage q : s.offlineQueue) {
                    psOffline.setString(1, s.clientId);
                    psOffline.setInt(2, order++);
                    psOffline.setString(3, q.topic);
                    psOffline.setBytes(4, q.payload);
                    psOffline.setInt(5, q.retain ? 1 : 0);
                    psOffline.setInt(6, q.qos);
                    psOffline.setLong(7, q.createdAtMs);
                    psOffline.addBatch();
                }
            }
            psIndex.executeBatch();
            psSub.executeBatch();
            psOffline.executeBatch();
        }
    }

    private void clearTables(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM " + tableOffline);
            st.executeUpdate("DELETE FROM " + tableSubs);
            st.executeUpdate("DELETE FROM " + tableSessionIndex);
        }
    }

    private void initSchema() {
        try (Connection c = getConnection(); Statement st = c.createStatement()) {
            st.execute(DbStoreNaming.createTableIfNotExists(tableSessionIndex,
                    "(client_id VARCHAR(255) PRIMARY KEY)"));
            st.execute(DbStoreNaming.createTableIfNotExists(tableSubs,
                    "(client_id VARCHAR(255) NOT NULL, topic_filter VARCHAR(1024) NOT NULL, qos INT NOT NULL,"
                            + " PRIMARY KEY(client_id, topic_filter))"));
            st.execute(DbStoreNaming.createTableIfNotExists(tableOffline,
                    "(client_id VARCHAR(255) NOT NULL, msg_order INT NOT NULL, topic VARCHAR(1024) NOT NULL,"
                            + " payload BLOB NOT NULL, retain_flag INT NOT NULL, qos INT NOT NULL, created_at BIGINT NOT NULL,"
                            + " PRIMARY KEY(client_id, msg_order))"));
            try {
                st.execute("ALTER TABLE " + tableOffline + " ADD COLUMN created_at BIGINT NOT NULL DEFAULT 0");
            } catch (SQLException ignored) {
                // column may already exist
            }
        } catch (SQLException e) {
            throw new IllegalStateException("初始化 DB Session 表失败", e);
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, username, password);
    }
}

