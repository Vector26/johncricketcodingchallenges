package org.example;

import org.example.loadbalancer.ConsistentHasher;
import org.example.server.Client;
import org.example.server.Server;

public class Main {
    public static void main(String[] args) {
        Server[] servers = new Server[10];
        int portRange = 1004;
        ConsistentHasher consistentHasher = new ConsistentHasher();
        for(int  i = 0;i < 10;i++)
        {
            servers[i] = new Server(portRange + i);
            new Thread(servers[i]).start();
            consistentHasher.addServer("localhost:" + (portRange + i));
        }
        Server loadBalancerServer = new Server(1003,consistentHasher);
        Thread loadBalancerThread = new Thread(loadBalancerServer);
        loadBalancerThread.start();
    }
}
