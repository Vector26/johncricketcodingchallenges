package org.johncricketCodingChallenges.loadBalancer.loadbalancer;

import java.util.PriorityQueue;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

public class DynamicWeightedRoundRobin implements LoadBalancer {
    private static class Server {
        String address;
        int load;

        Server(String address) {
            this.address = address;
            this.load = 0;
        }

        void increaseLoad() {
            this.load++;
        }

        void decreaseLoad() {
            this.load--;
        }
    }

    private PriorityQueue<Server> serverQueue;
    private Map<String, Server> serverMap;

    public DynamicWeightedRoundRobin() {
        serverQueue = new PriorityQueue<>(Comparator.comparingInt(s -> s.load));
        serverMap = new HashMap<>();
    }

    @Override
    public synchronized void addServer(String serverAddress) {
        Server server = new Server(serverAddress);
        serverQueue.offer(server);
        serverMap.put(serverAddress, server);
    }

    @Override
    public synchronized void removeServer(String serverAddress) {
        Server server = serverMap.get(serverAddress);
        if (server != null) {
            serverQueue.remove(server);
            serverMap.remove(serverAddress);
        }
    }

    @Override
    public synchronized String getServer(String request) {
        if (serverQueue.isEmpty()) {
            return null;
        }
        Server server = serverQueue.poll();
        server.increaseLoad();
        serverQueue.offer(server);
        return server.address;
    }

    @Override
    public synchronized void relieveServer(String serverAddress) {
        Server server = serverMap.get(serverAddress);
        if (server != null) {
            serverQueue.remove(server);
            server.decreaseLoad();
            serverQueue.offer(server);
        }
    }
}

