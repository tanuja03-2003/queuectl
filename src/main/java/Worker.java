package com.queuectl;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.sql.SQLException;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

public class Worker implements Runnable {
    private final String id;
    private final com.queuectl.JobStore store;
    private final AtomicBoolean running;
    private volatile boolean stopRequested = false;

    public Worker(String id, com.queuectl.JobStore store, AtomicBoolean running) {
        this.id = id; this.store = store; this.running = running;
    }

    public void requestStop(){ stopRequested = true; }

    @Override
    public void run() {
        System.out.println(id + " started.");
        while (running.get() && !stopRequested) {
            try {
                com.queuectl.Job job = store.pickPendingAndMarkProcessing(id);
                if (job == null) {
                    Thread.sleep(500); // nothing to do
                    continue;
                }
                System.out.println(id + " picked job " + job.getId() + " -> " + job.getCommand());
                // execute command
                ProcessBuilder pb = new ProcessBuilder();
                // run via shell so "sleep 2" or "echo hi" works
                if (isWindows()) {
                    pb.command("cmd.exe","/c", job.getCommand());
                } else {
                    pb.command("sh","-c", job.getCommand());
                }
                Process p = pb.start();
                BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
                BufferedReader brErr = new BufferedReader(new InputStreamReader(p.getErrorStream()));
                String line;
                while ((line = br.readLine()) != null) System.out.println("[" + job.getId() + "] " + line);
                while ((line = brErr.readLine()) != null) System.err.println("[" + job.getId() + " ERROR] " + line);
                int exit = p.waitFor();

                if (exit == 0) {
                    job.setState("completed");
                    job.setUpdatedAt(Instant.now().toString());
                    store.updateJob(job);
                    System.out.println(id + " completed " + job.getId());
                } else {
                    // failed -> increment attempts, decide retry or move to dead
                    job.setAttempts(job.getAttempts() + 1);
                    job.setUpdatedAt(Instant.now().toString());
                    int max = job.getMaxRetries();
                    if (job.getAttempts() > max) {
                        job.setState("dead");
                        store.updateJob(job);
                        System.out.println(id + " moved to DLQ " + job.getId());
                    } else {
                        job.setState("pending"); // requeue
                        store.updateJob(job);
                        // exponential backoff
                        double base = Double.parseDouble(store.getConfig("backoff_base"));
                        long delaySec = (long) Math.pow(base, job.getAttempts());
                        System.out.println(id + " will retry " + job.getId() + " after " + delaySec + "s (attempt " + job.getAttempts() + ")");
                        Thread.sleep(delaySec * 1000L);
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
                try { Thread.sleep(1000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        System.out.println(id + " exiting.");
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}
