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
    private Threadpool threadpool;
    private LoadBalancer loadBalancer;

    public Server(int port,int poolSize, LoadBalancer loadBalancer) {
        this.port = port;
        this.threadpool = new Threadpool(poolSize, "Server on port " + port);
        this.loadBalancer = loadBalancer;
        System.out.println("Server initialized with load balancer on port " + port);
    }

    public Server(int port,int poolSize) {
        this.port = port;
        this.threadpool = new Threadpool(poolSize, "Server on port " + port);
        this.loadBalancer = null;
        System.out.println("Server initialized without load balancer on port " + port);
    }


    private void log(String message) {
        System.out.println("[" + getServerType()+ " : " + (port) + "] : " + message);
    }

    private void logError(String action, Exception e) {
        System.out.println("Error in "+ getServerType() + " :  " + action + " : " + e.getMessage());
        e.printStackTrace();
    }
    @Override
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            log("Server listening");

            while (!Thread.currentThread().isInterrupted()) {
                Socket clientSocket = serverSocket.accept();
                log("Client connected");

                // Decide how to handle the client based on whether a load balancer is present
                if (getServerType() == "Server") {
                    log("Handling client directly");
                    this.threadpool.addAndExecute(new ClientHandler(clientSocket, port));
                } else {
                    log("[" + getServerType() + " on port " + port + "] Handling client via load balancer");
                    this.threadpool.addAndExecute(new ClientHandler(clientSocket, loadBalancer, port));
                }
            }
        } catch (IOException e) {
            logError("[" + getServerType() + " on port " + port + "] Server exception: " + e.getMessage(),e);
        }
    }

    private String getServerType() {
        return (loadBalancer == null) ? "Server" : "Load Balancer";
    }

    public class ClientHandler implements Runnable {
        private Socket clientSocket;
        private LoadBalancer loadBalancer;
        private int port;

        // Constructor for handling client requests directly (without load balancer)
        public ClientHandler(Socket clientSocket, int serverPort) {
            this.clientSocket = clientSocket;
            this.loadBalancer = null;
            this.port = serverPort;
            log("ClientHandler created without consistent hasher.");
        }

        // Constructor for handling client requests via load balancer
        public ClientHandler(Socket clientSocket, LoadBalancer loadBalancer, int serverPort) {
            this.clientSocket = clientSocket;
            this.loadBalancer = loadBalancer;
            this.port = serverPort;
            log("ClientHandler created with load balancer");
        }

        @Override
        public void run() {
            log("ClientHandler started.");
            if (loadBalancer != null) {
                handleRequestViaLoadBalancer();
            } else {
                handleDirectClientRequest();
            }
        }

        private void handleRequestViaLoadBalancer() {
            try (BufferedReader clientReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {
                log("Reading request from client...");
                String requestLine = clientReader.readLine();
                log("Request line: " + requestLine);
                String targetServer = loadBalancer.getServer(requestLine);
                log("Target server: " + targetServer);

                forwardRequest(targetServer, requestLine, clientReader);
            } catch (IOException e) {
                logError("handling request via load balancer", e);
            } finally {
                closeSocket();
            }
        }

        private void handleDirectClientRequest() {
            log("Handling direct client request...");
            try (BufferedReader clientReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                 PrintWriter clientWriter = new PrintWriter(clientSocket.getOutputStream(), true)) {

                String request = readRequest(clientReader);
                String responseMessage = "Hello from Server " + (port);
                sendResponse(clientWriter, responseMessage, "text/plain; charset=utf-8");

                log("Sent response: " + responseMessage);
            } catch (IOException e) {
                logError("handling direct client request", e);
            } finally {
                closeSocket();
            }
        }

        // Forward the request to the target server
        private void forwardRequest(String serverAddress, String initialRequestLine, BufferedReader clientReader) {
            log("Forwarding request to: " + serverAddress);

            String[] addressParts = serverAddress.split(":");
            if (addressParts.length < 2) {
                log("Invalid server address: " + serverAddress);
                return;
            }

            int targetPort = Integer.parseInt(addressParts[1]);
            try (Socket forwardSocket = new Socket(addressParts[0], targetPort);
                 PrintWriter serverWriter = new PrintWriter(forwardSocket.getOutputStream(), true); // LoadBalancer -> Server
                 BufferedReader serverReader = new BufferedReader(new InputStreamReader(forwardSocket.getInputStream())); // Server -> LoadBalancer
                 PrintWriter clientWriter = new PrintWriter(clientSocket.getOutputStream(), true)) { // Load Balancer -> Client

                forwardInitialRequest(initialRequestLine, serverWriter);
                readAndForwardHeaders(clientReader, serverWriter);
                loadBalancer.relieveServer(serverAddress);
                readAndForwardResponse(serverReader, clientWriter);

                log("Response forwarded successfully to client.");
            } catch (IOException e) {
                logError("forwarding request", e);
            } finally {
                closeSocket();
            }
        }

        private void forwardInitialRequest(String initialRequestLine, PrintWriter serverWriter) {
            serverWriter.println(initialRequestLine);
        }

        private void readAndForwardHeaders(BufferedReader clientReader, PrintWriter serverWriter) throws IOException {
            String line;
            while ((line = clientReader.readLine()) != null) {
                serverWriter.println(line);
                if (line.isEmpty()) break;
            }
            serverWriter.println();
        }

        private void readAndForwardResponse(BufferedReader serverReader, PrintWriter clientWriter) throws IOException {
            String line;
            while ((line = serverReader.readLine()) != null) {
                clientWriter.println(line);
            }
        }

        private String readRequest(BufferedReader reader) throws IOException {
            StringBuilder requestBuilder = new StringBuilder();
            String line;
            while (!(line = reader.readLine()).isEmpty()) {
                requestBuilder.append(line).append("\n");
                log("Received request line: " + line);
            }
            return requestBuilder.toString();
        }

        private void sendResponse(PrintWriter writer, String response, String contentType) {
            writer.println("HTTP/1.1 200 OK");
            writer.println("Content-Type: " + contentType);
            writer.println("Content-Length: " + response.length());
            writer.println();
            writer.println(response);
        }

        private void closeSocket() {
            try {
                if (clientSocket != null && !clientSocket.isClosed()) {
                    clientSocket.close();
                    log("Client socket closed.");
                }
            } catch (IOException e) {
                logError("closing client socket", e);
            }
        }
    }
}
