package com.mars.linker.broker.netty.store;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * DB 版 RetainStore，使用连接池。
 * <p>
 * 线程安全策略：依赖连接池并发能力，所有方法无需 synchronized。
 * </p>
 */
public final class DbRetainStore implements RetainStore {
    private static final Logger log = LoggerFactory.getLogger(DbRetainStore.class);

    private final DataSource dataSource;
    private final String tableRetain;

    public DbRetainStore(String jdbcUrl, String username, String password, String schema, String tablePrefix) {
        this.dataSource = new DbSessionStore.SimpleDriverDataSource(jdbcUrl, username, password);
        this.tableRetain = DbStoreNaming.prefix(schema, tablePrefix) + "retain";
        initSchema();
    }

    public DbRetainStore(DataSource dataSource, String schema, String tablePrefix) {
        this.dataSource = dataSource;
        this.tableRetain = DbStoreNaming.prefix(schema, tablePrefix) + "retain";
        initSchema();
    }

    @Override
    public void put(String topic, byte[] payload, int qos) {
        if (topic == null || payload == null) {
            return;
        }
        try (Connection c = getConnection()) {
            try (PreparedStatement del = c.prepareStatement("DELETE FROM " + tableRetain + " WHERE topic=?")) {
                del.setString(1, topic);
                del.executeUpdate();
            }
            try (PreparedStatement ins = c.prepareStatement(
                    "INSERT INTO " + tableRetain + "(topic, payload, qos) VALUES (?, ?, ?)")) {
                ins.setString(1, topic);
                ins.setBytes(2, payload);
                ins.setInt(3, qos);
                ins.executeUpdate();
            }
        } catch (SQLException e) {
            log.warn("DbRetainStore put 失败 topic={}", topic, e);
        }
    }

    @Override
    public void remove(String topic) {
        if (topic == null) {
            return;
        }
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM " + tableRetain + " WHERE topic=?")) {
            ps.setString(1, topic);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.warn("DbRetainStore remove 失败 topic={}", topic, e);
        }
    }

    @Override
    public List<RetainedMessage> list() {
        List<RetainedMessage> out = new ArrayList<>();
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT topic, payload, qos FROM " + tableRetain);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new RetainedMessage(rs.getString(1), rs.getBytes(2), rs.getInt(3)));
            }
        } catch (SQLException e) {
            log.warn("DbRetainStore list 失败", e);
        }
        return out;
    }

    private void initSchema() {
        try (Connection c = getConnection(); Statement st = c.createStatement()) {
            st.execute(DbStoreNaming.createTableIfNotExists(tableRetain,
                    "(topic VARCHAR(1024) PRIMARY KEY, payload BLOB NOT NULL, qos INT NOT NULL)"));
        } catch (SQLException e) {
            throw new IllegalStateException("初始化 DB Retain 表失败", e);
        }
    }

    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
}
