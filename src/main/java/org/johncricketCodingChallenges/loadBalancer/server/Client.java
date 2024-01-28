package org.johncricketCodingChallenges.loadBalancer.server;

import java.io.IOException;
import java.net.Socket;

public class Client implements Runnable{

    int port  = 9090;
    String hostname = "localhost";

    public Client() {
    }

    public Client(int port, String hostname) {
        this.port = port;
        this.hostname = hostname;
    }

    @Override
    public void run()
    {
        try (Socket socket = new Socket(hostname, port))
        {
            System.out.println("Connected to the server");
        }
        catch (IOException e)
        {
            System.out.println("Client exception: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
