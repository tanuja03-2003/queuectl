package com.queuectl;

import java.util.HashMap;
import java.util.Map;

public class Job {
    private String id;
    private String command;
    private String state;
    private int attempts;
    private int maxRetries = 3;
    private String createdAt;
    private String updatedAt;

    // getters/setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public static Job fromMap(Map<String,Object> m){
        Job j = new Job();
        if (m.containsKey("id")) j.setId(String.valueOf(m.get("id")));
        if (m.containsKey("command")) j.setCommand(String.valueOf(m.get("command")));
        if (m.containsKey("state")) j.setState(String.valueOf(m.get("state")));
        if (m.containsKey("attempts")) j.setAttempts(Integer.parseInt(String.valueOf(m.get("attempts"))));
        if (m.containsKey("max_retries")) j.setMaxRetries(Integer.parseInt(String.valueOf(m.get("max_retries"))));
        if (m.containsKey("created_at")) j.setCreatedAt(String.valueOf(m.get("created_at")));
        if (m.containsKey("updated_at")) j.setUpdatedAt(String.valueOf(m.get("updated_at")));
        return j;
    }

    public Map<String,Object> toMap(){
        Map<String,Object> m = new HashMap<>();
        m.put("id", id);
        m.put("command", command);
        m.put("state", state);
        m.put("attempts", attempts);
        m.put("max_retries", maxRetries);
        m.put("created_at", createdAt);
        m.put("updated_at", updatedAt);
        return m;
    }

    @Override
    public String toString(){
        return String.format("Job{id='%s', command='%s', state='%s', attempts=%d, max_retries=%d}", id, command, state, attempts, maxRetries);
    }
}
