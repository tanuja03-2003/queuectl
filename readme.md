QueueCTL (Java)

A simple CLI-based background job queue system built in Java.
Supports enqueueing jobs, running workers, retry mechanism, and a Dead Letter Queue (DLQ).
Job data is stored using SQLite.

Features

Enqueue jobs using JSON input
SQLite-based persistent storage
Multi-worker support
Command execution for each job
Retry mechanism (exponential backoff)
Dead Letter Queue (DLQ) for failed jobs
CLI commands for listing jobs, checking status, and retrying DLQ jobs

CLI Commands
Command	Description
enqueue <json>	Add a new job
worker start --count N	Start N workers
worker stop	Stop workers
list --state <state>	List jobs by state
status	Show job counts and active workers
dlq list	Show dead jobs
dlq retry <id>	Retry a dead job
How to Build
mvn clean package


This generates:

target/queuectl-1.0-SNAPSHOT.jar

How to Run (Windows CMD)
Enqueue a job:
java -jar target\queuectl-1.0-SNAPSHOT.jar enqueue "{\"id\":\"job1\",\"command\":\"echo hi\",\"max_retries\":2}"

Start workers:
java -jar target\queuectl-1.0-SNAPSHOT.jar worker start --count 1

Check status:
java -jar target\queuectl-1.0-SNAPSHOT.jar status

Project Structure
src/main/java/com/queuectl/
 ├── Main.java
 ├── Job.java
 ├── JobStore.java
 ├── Worker.java
 └── WorkerManager.java

