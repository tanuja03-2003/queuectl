package com.queuectl;

import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class WorkerManager {
    private final com.queuectl.JobStore store;
    private final List<com.queuectl.Worker> workers = new ArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(false);

    public WorkerManager(com.queuectl.JobStore store) {
        this.store = store;
    }

    public synchronized void startWorkers(int count) {
        if (running.get()) return;
        running.set(true);
        for (int i = 0; i < count; i++) {
            com.queuectl.Worker w = new com.queuectl.Worker("worker-" + i, store, running);
            workers.add(w);
            Thread t = new Thread(w);
            t.setDaemon(false);
            t.start();
        }
    }

    public synchronized void stopWorkers() {
        running.set(false);
        for (com.queuectl.Worker w : workers) w.requestStop();
        workers.clear();
    }

    public int getActiveCount() { return running.get() ? workers.size() : 0; }
}
