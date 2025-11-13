package com.queuectl;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class Main {
    private static final Path DB_PATH = Path.of("queue.db");

    public static void main(String[] args) throws Exception {
        // init DB & store
        com.queuectl.JobStore store = new com.queuectl.JobStore(DB_PATH.toString());
        store.init();
        WorkerManager wm = new WorkerManager(store);

        if (args.length == 0) {
            printHelp();
            return;
        }

        String cmd = args[0];
        switch (cmd) {
            case "enqueue":
                // e.g. queuectl enqueue '{"id":"job1","command":"echo hello"}'
                if (args.length < 2) {
                    System.err.println("Usage: enqueue '<json>'");
                    return;
                }
                String raw = joinArgs(args,1);
                ObjectMapper om = new ObjectMapper();
                Map<String,Object> map = om.readValue(raw, Map.class);
                com.queuectl.Job j = com.queuectl.Job.fromMap(map);
                if (j.getId() == null || j.getCommand() == null) {
                    System.err.println("job must have id and command");
                    return;
                }
                j.setState("pending");
                j.setAttempts(0);
                j.setCreatedAt(Instant.now().toString());
                j.setUpdatedAt(Instant.now().toString());
                store.insertJob(j);
                System.out.println("Enqueued -> " + j.getId());
                break;

            case "worker":
                if (args.length < 2) {
                    System.err.println("Usage: worker start --count N | worker stop");
                    return;
                }
                if ("start".equals(args[1])) {
                    int count = 1;
                    for (int i = 2; i < args.length; i++) {
                        if ("--count".equals(args[i]) && i+1 < args.length) {
                            count = Integer.parseInt(args[i+1]);
                            i++;
                        }
                    }
                    wm.startWorkers(count);
                    System.out.println("Workers started: " + count + ". Press ENTER in this terminal to stop.");
                    // block on stdin to allow graceful shutdown via Enter
                    new Scanner(System.in).nextLine();
                    wm.stopWorkers();
                    System.out.println("Workers stopped.");
                } else if ("stop".equals(args[1])) {
                    wm.stopWorkers();
                    System.out.println("Stop issued.");
                } else {
                    System.err.println("Unknown worker command: " + args[1]);
                }
                break;

            case "status":
                Map<String,Integer> counts = store.countByState();
                int active = wm.getActiveCount();
                System.out.println("Active workers: " + active);
                System.out.println("Jobs:");
                counts.forEach((k,v)-> System.out.printf("  %s: %d%n", k, v));
                break;

            case "list":
                String state = null;
                for (int i=1;i<args.length;i++){
                    if ("--state".equals(args[i]) && i+1<args.length) state=args[i+1];
                }
                List<com.queuectl.Job> jobs = store.listJobs(state);
                jobs.forEach(System.out::println);
                break;

            case "dlq":
                if (args.length < 2) {
                    System.err.println("Usage: dlq list | dlq retry <id>");
                    return;
                }
                if ("list".equals(args[1])) {
                    List<com.queuectl.Job> dlq = store.listJobs("dead");
                    dlq.forEach(System.out::println);
                } else if ("retry".equals(args[1]) && args.length>2) {
                    String id = args[2];
                    com.queuectl.Job job = store.getJob(id);
                    if (job == null) { System.err.println("no job"); return; }
                    job.setState("pending");
                    job.setAttempts(0);
                    job.setUpdatedAt(Instant.now().toString());
                    store.updateJob(job);
                    System.out.println("Requeued " + id);
                } else {
                    System.err.println("dlq unknown");
                }
                break;

            case "config":
                if (args.length >= 4 && "set".equals(args[1])) {
                    String key = args[2];
                    String val = args[3];
                    store.setConfig(key, val);
                    System.out.println("Config set " + key + "=" + val);
                } else {
                    System.out.println("Usage: config set <key> <value>");
                }
                break;

            default:
                System.err.println("Unknown command: " + cmd);
                printHelp();
        }

        // close resources
        store.close();
    }

    static String joinArgs(String[] a, int start) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < a.length; i++) {
            if (i > start) sb.append(" ");
            sb.append(a[i]);
        }
        return sb.toString();
    }

    static void printHelp(){
        System.out.println("queuectl commands:");
        System.out.println("  enqueue '<json>'");
        System.out.println("  worker start --count N");
        System.out.println("  worker stop");
        System.out.println("  status");
        System.out.println("  list --state pending|processing|completed|failed|dead");
        System.out.println("  dlq list");
        System.out.println("  dlq retry <id>");
        System.out.println("  config set <key> <value>  (keys: max_retries, backoff_base)");
    }
}
