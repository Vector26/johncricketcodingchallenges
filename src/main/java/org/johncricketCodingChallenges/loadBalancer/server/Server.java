package org.johncricketCodingChallenges.loadBalancer.server;

import org.johncricketCodingChallenges.loadBalancer.loadbalancer.ConsistentHasher;
import org.johncricketCodingChallenges.loadBalancer.loadbalancer.LoadBalancer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class Server implements Runnable {
    private int port;
    private int poolSize = 10;
    private Threadpool threadpool;
    private LoadBalancer loadBalancer;

    public Server(int port, LoadBalancer loadBalancer) {
        this.port = port;
        this.threadpool = new Threadpool(poolSize, "Server on port " + port);
        this.loadBalancer = loadBalancer;
        System.out.println("Server initialized with load balancer on port " + port);
    }

    public Server(int port) {
        this.port = port;
        this.threadpool = new Threadpool(poolSize, "Server on port " + port);
        this.loadBalancer = null;
        System.out.println("Server initialized without load balancer on port " + port);
    }

    @Override
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("[" + getServerType() + " on port " + port + "] Server listening");

            while (!Thread.currentThread().isInterrupted()) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("[" + getServerType() + " on port " + port + "] Client connected");

                // Decide how to handle the client based on whether a load balancer is present
                if (loadBalancer == null) {
                    System.out.println("[" + getServerType() + " on port " + port + "] Handling client directly");
                    this.threadpool.addAndExecute(new ClientHandler(clientSocket, port));
                } else {
                    System.out.println("[" + getServerType() + " on port " + port + "] Handling client via load balancer");
                    this.threadpool.addAndExecute(new ClientHandler(clientSocket, loadBalancer, port));
                }
            }
        } catch (IOException e) {
            System.out.println("[" + getServerType() + " on port " + port + "] Server exception: " + e.getMessage());
        }
    }

    private String getServerType() {
        return (loadBalancer == null) ? "Server" : "Load Balancer";
    }

    private static class ClientHandler implements Runnable {
        private Socket clientSocket; // Socket representing the connection to the client
        private LoadBalancer loadBalancer; // Used for determining the target server when load balancing
        private int port; // The port number of the server or load balancer

        // Constructor for handling client requests directly (without load balancer)
        public ClientHandler(Socket clientSocket, int serverPort) {
            this.clientSocket = clientSocket;
            this.loadBalancer = null;
            this.port = serverPort;
            // Log indicating the creation of a client handler without a consistent hasher
            System.out.println("[Server on port " + serverPort + "] ClientHandler created without consistent hasher.");
        }

        // Constructor for handling client requests via load balancer
        public ClientHandler(Socket clientSocket, LoadBalancer loadBalancer, int serverPort) {
            this.clientSocket = clientSocket;
            this.loadBalancer = loadBalancer;
            this.port = serverPort;
            // Log indicating the creation of a client handler with a consistent hasher (load balancer)
            System.out.println("[Load Balancer on port " + serverPort + "] ClientHandler created with consistent hasher.");
        }

        @Override
        public void run() {
            // Log indicating the start of the client handler's processing
            System.out.println("[Handler on port " + port + "] ClientHandler started.");

            // Check if this handler is being used in a load balancing context
            if (loadBalancer != null) {
                // Open a BufferedReader to read data from the client
                try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {
                    // Log for reading the request from the client
                    System.out.println("Reading request from client...");
                    String requestLine = in.readLine(); // Read the request line (e.g., "GET / HTTP/1.1")
                    System.out.println("Request line: " + requestLine);
                    String targetServer = loadBalancer.getServer(requestLine); // Determine the target server using the consistent hasher
                    System.out.println("Target server determined by consistent hasher: " + targetServer);

                    // Forward the request to the determined target server
                    forwardRequest(targetServer, requestLine, in);
                } catch (IOException e) {
                    // Log any exceptions that occur while handling the client request
                    System.out.println("ClientHandler exception: " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    // Close the client socket in the final block to ensure it's always closed
                    try {
                        clientSocket.close();
                        System.out.println("Client socket closed.");
                    } catch (IOException e) {
                        System.out.println("Error closing client socket: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            } else {
                // If there's no load balancer, handle the client request directly
                handleDirectClientRequest();
            }
        }

        // Method for handling direct client requests
        private void handleDirectClientRequest() {
            // Log indicating direct handling of the client request
            System.out.println("Handling direct client request...");
            try {
                // Open a BufferedReader to read the client's request and a PrintWriter to send a response
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

                // Read the client's request line by line for logging
                String requestLine;
                while (!(requestLine = in.readLine()).isEmpty()) {
                    System.out.println("[Server " + (port - 1003) + "] Received request line: " + requestLine);
                }

                // Create a response message including the server's position
                String responseMessage = "Hello from Server " + (port - 1003);
                // Write the HTTP response headers
                out.println("HTTP/1.1 200 OK");
                out.println("Content-Type: text/plain; charset=utf-8");
                out.println("Content-Length: " + responseMessage.length());
                out.println(); // End of the HTTP headers
                // Write the response body
                out.println(responseMessage);

                // Log the response sent to the client
                System.out.println("[Server " + (port - 1003) + "] Sent response: " + responseMessage);
            } catch (IOException e) {
                // Log any IO exceptions that occur
                System.out.println("Error handling direct client request: " + e.getMessage());
                e.printStackTrace();
            } finally {
                // Close the client socket in the final block to ensure it's always closed
                try {
                    clientSocket.close();
                    System.out.println("Client socket closed in direct client request handler.");
                } catch (IOException e) {
                    System.out.println("Error closing client socket in direct client request handler: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }

        // Method for forwarding the request to the target server
        private void forwardRequest(String serverAddress, String initialRequestLine, BufferedReader clientReader) {
            // Log indicating the start of the forwarding process
            System.out.println("[Handler on port " + port + "] Forwarding request to: " + serverAddress);
            // Split the server address into hostname and port
            String[] parts = serverAddress.split(":");
            if (parts.length < 2) {
                System.out.println("Invalid server address: " + serverAddress);
                return;
            }
            int targetPort = Integer.parseInt(parts[1]);

            // Establish a new socket connection to the target server
            try (Socket forwardSocket = new Socket(parts[0], targetPort);
                 PrintWriter serverWriter = new PrintWriter(forwardSocket.getOutputStream(), true);
                 BufferedReader serverReader = new BufferedReader(new InputStreamReader(forwardSocket.getInputStream()));
                 PrintWriter clientWriter = new PrintWriter(clientSocket.getOutputStream(), true)) {

                // Forward the request line and headers to the target server
                serverWriter.println(initialRequestLine);
                String line;
                while ((line = clientReader.readLine()) != null) {
                    serverWriter.println(line);
                    if (line.isEmpty()) break; // Detect end of HTTP headers
                }
                serverWriter.println(); // Ensure request is terminated properly

                //Relieving the server from client processing in the load balancer
                loadBalancer.relieveServer(serverAddress);

                // Read the response from the target server and forward it to the client
                while ((line = serverReader.readLine()) != null) {
                    clientWriter.println(line);
                }

                // Log indicating successful forwarding of the response
                System.out.println("Response forwarded successfully to client.");
            } catch (IOException e) {
                // Log any exceptions that occur during forwarding
                System.out.println("Error forwarding request: " + e.getMessage());
                e.printStackTrace();
            } finally {
                // Close the client socket in the final block to ensure it's always closed
                try {
                    if (clientSocket != null && !clientSocket.isClosed()) {
                        clientSocket.close();
                        System.out.println("Client socket closed after forwarding.");
                    }
                } catch (IOException e) {
                    System.out.println("Error closing client socket after forwarding: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }
}
