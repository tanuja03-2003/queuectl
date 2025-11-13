package com.queuectl;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JobStore implements AutoCloseable {
    private final Connection conn;

    public JobStore(String dbFile) throws SQLException {
        String url = "jdbc:sqlite:" + dbFile;
        this.conn = DriverManager.getConnection(url);
        this.conn.setAutoCommit(false);
    }

    public void init() throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS jobs (
                  id TEXT PRIMARY KEY,
                  command TEXT NOT NULL,
                  state TEXT NOT NULL,
                  attempts INTEGER,
                  max_retries INTEGER,
                  created_at TEXT,
                  updated_at TEXT
                );
            """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS config (
                  key TEXT PRIMARY KEY,
                  value TEXT
                );
            """);
            // defaults
            PreparedStatement ps = conn.prepareStatement("INSERT OR IGNORE INTO config(key,value) VALUES(?,?)");
            ps.setString(1,"max_retries"); ps.setString(2,"3"); ps.execute();
            ps.setString(1,"backoff_base"); ps.setString(2,"2"); ps.execute();
            conn.commit();
        }
    }

    public void insertJob(com.queuectl.Job j) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO jobs(id,command,state,attempts,max_retries,created_at,updated_at) VALUES(?,?,?,?,?,?,?)")) {
            ps.setString(1, j.getId());
            ps.setString(2, j.getCommand());
            ps.setString(3, j.getState());
            ps.setInt(4, j.getAttempts());
            ps.setInt(5, j.getMaxRetries());
            ps.setString(6, j.getCreatedAt());
            ps.setString(7, j.getUpdatedAt());
            ps.executeUpdate();
            conn.commit();
        }
    }

    public com.queuectl.Job pickPendingAndMarkProcessing(String workerId) throws SQLException {
        // fetch one pending job and set to processing in a transaction to avoid duplicates
        com.queuectl.Job result = null;
        try (PreparedStatement sel = conn.prepareStatement(
                "SELECT id,command,state,attempts,max_retries,created_at,updated_at FROM jobs WHERE state='pending' ORDER BY created_at LIMIT 1")) {
            ResultSet rs = sel.executeQuery();
            if (rs.next()) {
                String id = rs.getString("id");
                result = new com.queuectl.Job();
                result.setId(id);
                result.setCommand(rs.getString("command"));
                result.setState("processing");
                result.setAttempts(rs.getInt("attempts"));
                result.setMaxRetries(rs.getInt("max_retries"));
                result.setCreatedAt(rs.getString("created_at"));
                result.setUpdatedAt(Instant.now().toString());
                // update row
                try (PreparedStatement upd = conn.prepareStatement("UPDATE jobs SET state=?, updated_at=? WHERE id=? AND state='pending'")) {
                    upd.setString(1, "processing");
                    upd.setString(2, result.getUpdatedAt());
                    upd.setString(3, id);
                    int changed = upd.executeUpdate();
                    if (changed == 1) {
                        conn.commit();
                        return result;
                    } else {
                        conn.rollback();
                        return null; // someone else took it
                    }
                }
            } else {
                conn.rollback();
                return null;
            }
        } catch (SQLException ex) {
            conn.rollback();
            throw ex;
        }
    }

    public void updateJob(com.queuectl.Job j) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE jobs SET command=?, state=?, attempts=?, max_retries=?, updated_at=? WHERE id=?")) {
            ps.setString(1, j.getCommand());
            ps.setString(2, j.getState());
            ps.setInt(3, j.getAttempts());
            ps.setInt(4, j.getMaxRetries());
            ps.setString(5, j.getUpdatedAt());
            ps.setString(6, j.getId());
            ps.executeUpdate();
            conn.commit();
        }
    }

    public com.queuectl.Job getJob(String id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT id,command,state,attempts,max_retries,created_at,updated_at FROM jobs WHERE id=?")) {
            ps.setString(1,id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                com.queuectl.Job j = new com.queuectl.Job();
                j.setId(rs.getString("id"));
                j.setCommand(rs.getString("command"));
                j.setState(rs.getString("state"));
                j.setAttempts(rs.getInt("attempts"));
                j.setMaxRetries(rs.getInt("max_retries"));
                j.setCreatedAt(rs.getString("created_at"));
                j.setUpdatedAt(rs.getString("updated_at"));
                return j;
            }
            return null;
        }
    }

    public List<com.queuectl.Job> listJobs(String state) throws SQLException {
        List<com.queuectl.Job> out = new ArrayList<>();
        String sql = "SELECT id,command,state,attempts,max_retries,created_at,updated_at FROM jobs";
        if (state != null) sql += " WHERE state = ?";
        sql += " ORDER BY created_at";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (state != null) ps.setString(1, state);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                com.queuectl.Job j = new com.queuectl.Job();
                j.setId(rs.getString("id"));
                j.setCommand(rs.getString("command"));
                j.setState(rs.getString("state"));
                j.setAttempts(rs.getInt("attempts"));
                j.setMaxRetries(rs.getInt("max_retries"));
                j.setCreatedAt(rs.getString("created_at"));
                j.setUpdatedAt(rs.getString("updated_at"));
                out.add(j);
            }
        }
        return out;
    }

    public Map<String,Integer> countByState() throws SQLException {
        Map<String,Integer> map = new HashMap<>();
        try (Statement st = conn.createStatement()) {
            ResultSet rs = st.executeQuery("SELECT state, COUNT(*) as c FROM jobs GROUP BY state");
            while (rs.next()) {
                map.put(rs.getString("state"), rs.getInt("c"));
            }
        }
        return map;
    }

    public String getConfig(String key) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT value FROM config WHERE key=?")) {
            ps.setString(1,key);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("value");
            return null;
        }
    }

    public void setConfig(String key, String value) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO config(key,value) VALUES(?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value")) {
            ps.setString(1,key);
            ps.setString(2,value);
            ps.executeUpdate();
            conn.commit();
        }
    }

    @Override
    public void close() throws Exception { conn.close(); }
}
