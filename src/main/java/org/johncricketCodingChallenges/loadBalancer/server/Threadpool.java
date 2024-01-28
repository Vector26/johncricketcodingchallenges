package org.johncricketCodingChallenges.loadBalancer.server;

import java.util.concurrent.LinkedBlockingQueue;

public class Threadpool {
    private final WorkerThread[] workers;
    private final LinkedBlockingQueue<Runnable> queue;
    private final String threadpoolContext; // To identify which server is using the threadpool

    public Threadpool(int poolSize, String context) {
        this.threadpoolContext = context; // context could be something like "Server on port 9090"
        this.workers = new WorkerThread[poolSize];
        this.queue = new LinkedBlockingQueue<>();

        for (int i = 0; i < poolSize; i++) {
            workers[i] = new WorkerThread();
            workers[i].setName(threadpoolContext + " - Worker " + (i + 1)); // Naming threads for easier debugging
            workers[i].start();
        }

        System.out.println("[" + threadpoolContext + "] Threadpool initialized with " + poolSize + " worker threads");
    }

    public void addAndExecute(Runnable task) {
        synchronized (queue) {
            queue.add(task);
            System.out.println("[" + threadpoolContext + "] Task added to queue. Total tasks in queue: " + queue.size());
            queue.notify();
        }
    }

    class WorkerThread extends Thread {
        @Override
        public void run() {
            Runnable task;
            while (true) {
                synchronized (queue) {
                    while (queue.isEmpty()) {
                        try {
                            System.out.println("[" + getName() + "] Waiting for tasks...");
                            queue.wait();
                        } catch (InterruptedException e) {
                            System.out.println("[" + getName() + "] Error in waiting for a queue: " + e.getMessage());
                            // Consider whether to break out of the loop upon interruption
                        }
                    }
                    task = queue.poll();
                    System.out.println("[" + getName() + "] Task retrieved from queue. Executing task...");
                }
                try {
                    task.run();
                } catch (RuntimeException e) {
                    System.out.println("[" + getName() + "] Error in running a task: " + e.getMessage());
                }
            }
        }
    }
}
